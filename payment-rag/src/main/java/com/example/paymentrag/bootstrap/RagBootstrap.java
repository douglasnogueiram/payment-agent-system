package com.example.paymentrag.bootstrap;

import com.example.paymentrag.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Loads the bundled payment policy document into ChromaDB on startup.
 * Only runs when rag.bootstrap.enabled=true (default).
 * Idempotent: if the document is already loaded with the same hash, it is skipped;
 * otherwise it is replaced with the current version.
 */
@Component
public class RagBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RagBootstrap.class);

    private final DocumentService documentService;
    private final boolean bootstrapEnabled;
    private final String documentName;
    private final Resource paymentPolicy;

    public RagBootstrap(
            DocumentService documentService,
            @Value("${rag.bootstrap.enabled:true}") boolean bootstrapEnabled,
            @Value("${rag.bootstrap.document-name:payment-policy}") String documentName,
            @Value("classpath:rag/payment-policy.txt") Resource paymentPolicy) {
        this.documentService = documentService;
        this.bootstrapEnabled = bootstrapEnabled;
        this.documentName = documentName;
        this.paymentPolicy = paymentPolicy;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!bootstrapEnabled) {
            log.info("Payment RAG bootstrap disabled (rag.bootstrap.enabled=false). Skipping.");
            return;
        }

        log.info("Payment RAG bootstrap starting — loading '{}'", documentName);
        String content = paymentPolicy.getContentAsString(StandardCharsets.UTF_8);
        var result = documentService.loadDocument(documentName, content);
        log.info("Payment RAG bootstrap complete — document '{}' {} ({} chunks)", result.name(), result.action(), result.chunks());
    }
}
