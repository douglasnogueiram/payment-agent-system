package com.example.banking.controller;

import com.example.banking.repository.TransactionRepository;
import com.example.banking.service.AccountService;
import com.example.banking.service.PixWebhookNotifier;
import com.example.banking.service.TransactionEventLogger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives asynchronous callback notifications from Celcoin.
 * No authentication required — Celcoin calls this endpoint from inside the Docker network.
 */
@RestController
@RequestMapping("/api/webhooks/celcoin")
public class WebhookController {

    private final AccountService service;
    private final PixWebhookNotifier webhookNotifier;
    private final TransactionEventLogger eventLogger;
    private final TransactionRepository txRepo;

    public WebhookController(AccountService service, PixWebhookNotifier webhookNotifier,
                              TransactionEventLogger eventLogger, TransactionRepository txRepo) {
        this.service = service;
        this.webhookNotifier = webhookNotifier;
        this.eventLogger = eventLogger;
        this.txRepo = txRepo;
    }

    /**
     * POST /api/webhooks/celcoin/pix
     * Expected payload: { "event": "pix-payment-out", "status": "PAID"|"FAILED",
     *                     "transactionId": "...", "endToEndId": "..." }
     *
     * If payPix() is still blocking on the future → complete it (payPix handles DB update).
     * If payPix() already timed out or returned    → update DB directly as fallback.
     */
    @PostMapping("/pix")
    public ResponseEntity<Map<String, Object>> pixCallback(@RequestBody Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String endToEndId    = (String) payload.get("endToEndId");
        String status        = (String) payload.get("status");

        // Log webhook receipt — resolve transactionId for the event
        Long txId = null;
        if (transactionId != null) {
            txRepo.findByCelcoinTransactionId(transactionId).ifPresent(tx ->
                eventLogger.log(tx.getId(), "WEBHOOK_RECEIVED",
                    String.format("Webhook Celcoin recebido: %s | TxId: %s | E2E: %s",
                        status, transactionId, endToEndId != null ? endToEndId : "")));
        } else if (endToEndId != null) {
            txRepo.findByEndToEndId(endToEndId).ifPresent(tx ->
                eventLogger.log(tx.getId(), "WEBHOOK_RECEIVED",
                    String.format("Webhook Celcoin recebido: %s | E2E: %s", status, endToEndId)));
        }

        // Try to notify a waiting payPix() call
        boolean notified = webhookNotifier.complete(endToEndId, status);
        if (!notified) notified = webhookNotifier.completeByTxId(transactionId, status);

        // Fallback: no future was waiting — update DB directly (e.g. after timeout)
        if (!notified) {
            service.updatePixTransactionStatus(transactionId, endToEndId, status);
        }

        return ResponseEntity.ok(Map.of("received", true));
    }
}
