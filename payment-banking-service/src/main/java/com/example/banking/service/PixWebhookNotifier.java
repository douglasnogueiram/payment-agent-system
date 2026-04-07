package com.example.banking.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry that allows payPix() to block until the Celcoin webhook arrives.
 *
 * Flow:
 *  1. payPix() calls register(endToEndId) BEFORE submitting to Celcoin → gets a Future
 *  2. payPix() blocks on future.get(timeout)
 *  3. WebhookController receives callback → calls complete(endToEndId, status) → unblocks payPix()
 *  4. If webhook doesn't arrive within timeout → payPix() calls cleanup() and returns PENDING
 */
@Component
public class PixWebhookNotifier {

    private final ConcurrentHashMap<String, CompletableFuture<String>> byEndToEnd  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>                    txIdToE2E   = new ConcurrentHashMap<>();

    /** Register a future keyed by endToEndId. Call this BEFORE submitting to Celcoin. */
    public CompletableFuture<String> register(String endToEndId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        byEndToEnd.put(endToEndId, future);
        return future;
    }

    /** Map Celcoin's transactionId → endToEndId so the webhook can resolve by either key. */
    public void mapTxId(String celcoinTransactionId, String endToEndId) {
        txIdToE2E.put(celcoinTransactionId, endToEndId);
    }

    /**
     * Complete the future for this endToEndId (called by WebhookController).
     * @return true if a waiting caller was notified, false if no future was registered.
     */
    public boolean complete(String endToEndId, String status) {
        if (endToEndId == null) return false;
        CompletableFuture<String> future = byEndToEnd.remove(endToEndId);
        if (future != null) {
            future.complete(status);
            return true;
        }
        return false;
    }

    /** Complete by Celcoin transactionId (fallback when endToEndId not in payload). */
    public boolean completeByTxId(String celcoinTransactionId, String status) {
        if (celcoinTransactionId == null) return false;
        String endToEndId = txIdToE2E.remove(celcoinTransactionId);
        return endToEndId != null && complete(endToEndId, status);
    }

    /** Remove stale entries on timeout or error. */
    public void cleanup(String endToEndId) {
        byEndToEnd.remove(endToEndId);
    }
}
