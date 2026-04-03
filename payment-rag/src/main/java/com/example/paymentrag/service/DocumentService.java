package com.example.paymentrag.service;

import com.example.paymentrag.domain.DocumentRecord;
import com.example.paymentrag.domain.DocumentVersion;
import com.example.paymentrag.repository.DocumentRecordRepository;
import com.example.paymentrag.repository.DocumentVersionRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int MAX_CHUNKS_PER_DOCUMENT = 1000;

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final DocumentRecordRepository recordRepository;
    private final DocumentVersionRepository versionRepository;

    public DocumentService(VectorStore vectorStore,
                           DocumentRecordRepository recordRepository,
                           DocumentVersionRepository versionRepository) {
        this.vectorStore = vectorStore;
        this.splitter = new TokenTextSplitter();
        this.recordRepository = recordRepository;
        this.versionRepository = versionRepository;
    }

    /**
     * Loads (or replaces) a document. Detects content type, extracts text,
     * computes SHA-256 hash, and skips processing if the file is identical.
     */
    @Transactional
    public LoadResult loadDocument(String name, String originalFilename, String contentType, byte[] bytes) throws IOException {
        String hash = sha256(bytes);
        long sizeBytes = bytes.length;

        Optional<DocumentRecord> existing = recordRepository.findByName(name);

        // If same hash and document is active — skip
        if (existing.isPresent() && "ACTIVE".equals(existing.get().getStatus())
                && hash.equals(existing.get().getSha256Hash())) {
            log.info("Document '{}' is unchanged (same hash {}), skipping", name, hash);
            DocumentRecord rec = existing.get();
            return new LoadResult(rec.getName(), rec.getOriginalFilename(), rec.getContentType(),
                    rec.getSizeBytes(), rec.getSha256Hash(), rec.getVersion(), rec.getChunks(), "unchanged");
        }

        String text = extractText(contentType, bytes);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Could not extract text from file (empty or unsupported format).");
        }

        // Delete existing chunks from vector store
        int deletedChunks = deleteFromVectorStore(name);
        String action = deletedChunks > 0 ? "updated" : "created";

        // Split and store in vector store
        Document raw = new Document(text, Map.of("source", name));
        List<Document> chunks = splitter.transform(List.of(raw));
        chunks.forEach(chunk -> chunk.getMetadata().put("source", name));
        vectorStore.add(chunks);

        // Persist registry
        int newVersion = 1;
        if (existing.isPresent()) {
            newVersion = existing.get().getVersion() + 1;
            DocumentRecord rec = existing.get();
            rec.setOriginalFilename(originalFilename);
            rec.setContentType(contentType);
            rec.setSizeBytes(sizeBytes);
            rec.setSha256Hash(hash);
            rec.setVersion(newVersion);
            rec.setChunks(chunks.size());
            rec.setStatus("ACTIVE");
            rec.setUploadedAt(LocalDateTime.now());
            recordRepository.save(rec);
        } else {
            DocumentRecord rec = new DocumentRecord();
            rec.setName(name);
            rec.setOriginalFilename(originalFilename);
            rec.setContentType(contentType);
            rec.setSizeBytes(sizeBytes);
            rec.setSha256Hash(hash);
            rec.setVersion(newVersion);
            rec.setChunks(chunks.size());
            rec.setStatus("ACTIVE");
            rec.setUploadedAt(LocalDateTime.now());
            recordRepository.save(rec);
        }

        // Persist version history
        DocumentVersion ver = new DocumentVersion();
        ver.setName(name);
        ver.setOriginalFilename(originalFilename);
        ver.setSha256Hash(hash);
        ver.setVersion(newVersion);
        ver.setChunks(chunks.size());
        ver.setAction(action.equals("created") ? "CREATED" : "UPDATED");
        ver.setUploadedAt(LocalDateTime.now());
        versionRepository.save(ver);

        log.info("Document '{}' {}: v{}, {} chunks (replaced {} old)", name, action, newVersion, chunks.size(), deletedChunks);
        return new LoadResult(name, originalFilename, contentType, sizeBytes, hash, newVersion, chunks.size(), action);
    }

    /**
     * Convenience overload for plain text (used by bootstrap).
     */
    @Transactional
    public LoadResult loadDocument(String name, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return loadDocument(name, name + ".txt", "text/plain", bytes);
    }

    /**
     * Deletes a document from vector store and marks registry entry as DELETED.
     */
    @Transactional
    public DeleteResult deleteDocument(String name) {
        int deleted = deleteFromVectorStore(name);

        Optional<DocumentRecord> existing = recordRepository.findByName(name);
        if (existing.isEmpty() && deleted == 0) {
            return new DeleteResult(name, 0, false);
        }

        if (existing.isPresent()) {
            DocumentRecord rec = existing.get();
            int version = rec.getVersion() + 1;

            DocumentVersion ver = new DocumentVersion();
            ver.setName(name);
            ver.setOriginalFilename(rec.getOriginalFilename());
            ver.setSha256Hash(rec.getSha256Hash());
            ver.setVersion(version);
            ver.setChunks(0);
            ver.setAction("DELETED");
            ver.setUploadedAt(LocalDateTime.now());
            versionRepository.save(ver);

            rec.setStatus("DELETED");
            rec.setChunks(0);
            rec.setVersion(version);
            rec.setUploadedAt(LocalDateTime.now());
            recordRepository.save(rec);
        }

        return new DeleteResult(name, deleted, true);
    }

    /**
     * Returns the number of chunks currently stored for a document.
     */
    public int countChunks(String name) {
        return findChunkIds(name).size();
    }

    /**
     * Returns the current registry record for a document, if it exists and is ACTIVE.
     */
    public Optional<DocumentRecord> findRecord(String name) {
        return recordRepository.findByName(name)
                .filter(r -> "ACTIVE".equals(r.getStatus()));
    }

    /**
     * Returns all active documents.
     */
    public List<DocumentRecord> listDocuments() {
        return recordRepository.findByStatusOrderByUploadedAtDesc("ACTIVE");
    }

    /**
     * Returns all versions for a document, newest first.
     */
    public List<DocumentVersion> listVersions(String name) {
        return versionRepository.findByNameOrderByVersionDesc(name);
    }

    // ─── private helpers ────────────────────────────────────────────────────────

    private int deleteFromVectorStore(String name) {
        List<String> ids = findChunkIds(name);
        if (!ids.isEmpty()) {
            vectorStore.delete(ids);
        }
        return ids.size();
    }

    private List<String> findChunkIds(String name) {
        var filter = new FilterExpressionBuilder().eq("source", name).build();
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(name)
                        .topK(MAX_CHUNKS_PER_DOCUMENT)
                        .filterExpression(filter)
                        .similarityThreshold(0.0)
                        .build());
        return results.stream().map(Document::getId).toList();
    }

    private String extractText(String contentType, byte[] bytes) throws IOException {
        if (contentType == null) contentType = "text/plain";

        if (contentType.contains("pdf")) {
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                return new PDFTextStripper().getText(doc);
            }
        }
        // Default: treat as UTF-8 text
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ─── result records ──────────────────────────────────────────────────────────

    public record LoadResult(
            String name,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String sha256Hash,
            int version,
            int chunks,
            String action  // created | updated | unchanged
    ) {}

    public record DeleteResult(String name, int deletedChunks, boolean found) {}
}
