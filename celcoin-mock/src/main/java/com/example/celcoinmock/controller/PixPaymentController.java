package com.example.celcoinmock.controller;

import com.example.celcoinmock.repository.PixKeyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Simulates Celcoin PIX cash-out (transfer) endpoint.
 * POST /pix/v2/transfer
 *
 * Validates amount, key existence, and returns PIE error codes on failure.
 * After accepting a transfer, fires an async webhook callback to the banking-service.
 */
@RestController
@RequestMapping("/pix/v2")
public class PixPaymentController {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_DAILY   = new BigDecimal("10000.00");

    private final PixKeyRepository pixKeyRepo;
    private final String bankingWebhookUrl;
    private final long webhookDelayMs;

    public PixPaymentController(
            PixKeyRepository pixKeyRepo,
            @Value("${banking.webhook.url:http://localhost:8081/api/webhooks/celcoin/pix}") String bankingWebhookUrl,
            @Value("${celcoin.mock.pix-webhook-delay-ms:3000}") long webhookDelayMs
    ) {
        this.pixKeyRepo = pixKeyRepo;
        this.bankingWebhookUrl = bankingWebhookUrl;
        this.webhookDelayMs = webhookDelayMs;
    }

    public record PixTransferRequest(
        String clientCode,
        String endToEndId,
        String pixAlias,       // destination PIX key
        Double amount,
        String description,
        Payer payer
    ) {}

    public record Payer(
        String documentNumber,
        String name,
        String branch,
        String accountNumber,
        String accountType
    ) {}

    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transfer(
            @RequestBody PixTransferRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return error(401, "PIE401", "Token de autenticação ausente ou inválido.");
        }
        if (req.clientCode() == null || req.clientCode().isBlank()) {
            return error(400, "PIE010", "clientCode é obrigatório.");
        }
        if (req.pixAlias() == null || req.pixAlias().isBlank()) {
            return error(400, "PIE002", "Chave Pix de destino é obrigatória.");
        }
        if (req.amount() == null) {
            return error(400, "PIE003", "O campo amount é obrigatório.");
        }

        BigDecimal amount = BigDecimal.valueOf(req.amount());

        if (amount.compareTo(MIN_AMOUNT) < 0) {
            return error(400, "PIE004", "Valor mínimo para transação Pix é R$ 0,01.");
        }
        if (amount.compareTo(MAX_DAILY) > 0) {
            return error(400, "PIE005", "Valor ultrapassa o limite diário de R$ 10.000,00.");
        }
        if (req.payer() == null) {
            return error(400, "PIE006", "Dados do pagador são obrigatórios.");
        }
        if (!pixKeyRepo.existsByKeyValue(req.pixAlias())) {
            return error(404, "PIE001", "Chave Pix não encontrada no DICT.");
        }

        // Success — simulate async processing (accepted)
        String transactionId = UUID.randomUUID().toString();
        String endToEndId = req.endToEndId() != null && !req.endToEndId().isBlank()
            ? req.endToEndId()
            : "E" + transactionId.replace("-", "").substring(0, 29).toUpperCase();

        // Fire async webhook to banking-service after delay
        scheduleWebhook(transactionId, endToEndId, amount, req.pixAlias());

        return ResponseEntity.ok(Map.of(
            "version", "1.0.0",
            "status", "PROCESSING",
            "body", Map.of(
                "transactionId", transactionId,
                "clientCode", req.clientCode(),
                "endToEndId", endToEndId,
                "amount", amount,
                "pixAlias", req.pixAlias(),
                "createDate", Instant.now().toString()
            )
        ));
    }

    private void scheduleWebhook(String transactionId, String endToEndId,
                                  BigDecimal amount, String pixAlias) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(webhookDelayMs);
                RestClient.create().post()
                    .uri(bankingWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                        "event",         "pix-payment-out",
                        "status",        "PAID",
                        "transactionId", transactionId,
                        "endToEndId",    endToEndId,
                        "amount",        amount,
                        "pixAlias",      pixAlias != null ? pixAlias : ""
                    ))
                    .retrieve()
                    .toBodilessEntity();
            } catch (Exception ignored) {
                // Webhook delivery failure is non-fatal for the mock
            }
        });
    }

    private ResponseEntity<Map<String, Object>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "version", "1.0.0",
            "status", "ERROR",
            "error", Map.of("errorCode", code, "message", message)
        ));
    }
}
