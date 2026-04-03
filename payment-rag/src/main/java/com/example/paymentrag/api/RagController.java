package com.example.paymentrag.api;

import com.example.paymentrag.domain.DocumentRecord;
import com.example.paymentrag.domain.DocumentVersion;
import com.example.paymentrag.service.DocumentService;
import com.example.paymentrag.service.DocumentService.LoadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Payment RAG Documents", description = "Manage payment policy documents in the ChromaDB vector store")
public class RagController {

    private final DocumentService documentService;

    public RagController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Upload (or replace) a document. Supports .txt and .pdf.
     * If the file hash matches the current version, returns action=unchanged without reprocessing.
     *
     * POST /api/documents/upload?name=payment-policy
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace a document",
            description = "Loads a .txt or .pdf file into ChromaDB. Deduplicates by SHA-256 hash. " +
                    "Returns action: created | updated | unchanged.")
    public ResponseEntity<LoadResult> upload(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file) throws IOException {

        validateName(name);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String contentType = file.getContentType();
        if (contentType == null) contentType = "application/octet-stream";

        if (!isSupportedType(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Tipo de arquivo não suportado. Use .txt ou .pdf.");
        }

        LoadResult result = documentService.loadDocument(
                name,
                file.getOriginalFilename(),
                contentType,
                file.getBytes());

        return ResponseEntity.ok(result);
    }

    /**
     * List all active documents with metadata.
     *
     * GET /api/documents
     */
    @GetMapping
    @Operation(summary = "List all documents", description = "Returns all active documents with metadata.")
    public ResponseEntity<List<DocumentRecord>> list() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    /**
     * Get current status and metadata for a document.
     *
     * GET /api/documents/{name}
     */
    @GetMapping("/{name}")
    @Operation(summary = "Get document status", description = "Returns metadata for the given document.")
    public ResponseEntity<DocumentRecord> status(@PathVariable String name) {
        return documentService.findRecord(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get version history for a document.
     *
     * GET /api/documents/{name}/versions
     */
    @GetMapping("/{name}/versions")
    @Operation(summary = "Get document version history", description = "Returns all versions of a document, newest first.")
    public ResponseEntity<List<DocumentVersion>> versions(@PathVariable String name) {
        List<DocumentVersion> versions = documentService.listVersions(name);
        if (versions.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(versions);
    }

    /**
     * Delete all chunks of a document from the vector store.
     *
     * DELETE /api/documents/{name}
     */
    @DeleteMapping("/{name}")
    @Operation(summary = "Delete a document", description = "Removes all chunks from ChromaDB and marks document as deleted.")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String name) {
        DocumentService.DeleteResult result = documentService.deleteDocument(name);
        if (!result.found()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("name", name, "deletedChunks", result.deletedChunks()));
    }

    private void validateName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nome inválido. Use apenas letras, números, hífen ou underscore.");
        }
    }

    private boolean isSupportedType(String contentType) {
        return contentType.contains("text/plain")
                || contentType.contains("text/")
                || contentType.contains("pdf");
    }
}
