package com.example.banking.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for generated PDF statement bytes.
 * Each PDF is keyed by a one-time token valid for 10 minutes.
 */
@Component
public class PdfStatementStore {

    private record Entry(byte[] data, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /** Stores PDF bytes and returns a 10-minute download token. */
    public String store(byte[] pdfBytes) {
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, new Entry(pdfBytes, Instant.now().plusSeconds(600)));
        return token;
    }

    /** Returns the PDF bytes if the token is valid and not expired. */
    public Optional<byte[]> get(String token) {
        Entry entry = store.get(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            store.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.data());
    }

    /** Periodically removes expired entries. */
    @Scheduled(fixedDelay = 120_000)
    public void cleanup() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
