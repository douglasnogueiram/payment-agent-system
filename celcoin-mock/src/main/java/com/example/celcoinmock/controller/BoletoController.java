package com.example.celcoinmock.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Simulates Celcoin boleto payment endpoint.
 * POST /boleto/v1/payment
 *
 * Validates barcode format and returns BLE error codes on failure.
 */
@RestController
@RequestMapping("/boleto/v1")
public class BoletoController {

    public record BoletoPaymentRequest(
        String clientCode,
        String barcode,        // linha digitável (47 ou 48 dígitos)
        Double amount,         // optional override; if null, extracted from barcode
        String description
    ) {}

    @PostMapping("/payment")
    public ResponseEntity<Map<String, Object>> payBoleto(
            @RequestBody BoletoPaymentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return error(401, "BLE401", "Token de autenticação ausente ou inválido.");
        }
        if (req.clientCode() == null || req.clientCode().isBlank()) {
            return error(400, "BLE010", "clientCode é obrigatório.");
        }
        if (req.barcode() == null || req.barcode().isBlank()) {
            return error(400, "BLE001", "Código de barras do boleto é obrigatório.");
        }

        String digits = req.barcode().replaceAll("\\D", "");

        if (digits.length() < 44) {
            return error(400, "BLE001", "Código de barras inválido. Deve conter pelo menos 44 dígitos numéricos.");
        }

        BigDecimal amount = extractAmount(digits, req.amount());

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return error(400, "BLE003", "Valor do boleto não pôde ser determinado ou é inválido.");
        }

        // Simulate expired boleto if barcode starts with "00000"
        if (digits.startsWith("00000")) {
            return error(400, "BLE002", "Boleto vencido. Data de vencimento ultrapassada.");
        }

        String transactionId = UUID.randomUUID().toString();
        String dueDate = LocalDate.now().plusDays(3).format(DateTimeFormatter.ISO_DATE);

        return ResponseEntity.ok(Map.of(
            "version", "1.0.0",
            "status", "PROCESSING",
            "body", Map.of(
                "transactionId", transactionId,
                "clientCode", req.clientCode(),
                "barcode", req.barcode(),
                "amount", amount,
                "dueDate", dueDate,
                "createDate", java.time.Instant.now().toString()
            )
        ));
    }

    private BigDecimal extractAmount(String digits, Double override) {
        if (override != null && override > 0) return BigDecimal.valueOf(override);
        try {
            if (digits.length() >= 10) {
                long cents = Long.parseLong(digits.substring(digits.length() - 10));
                if (cents > 0) return BigDecimal.valueOf(cents, 2);
            }
        } catch (NumberFormatException ignored) {}
        return BigDecimal.ZERO;
    }

    private ResponseEntity<Map<String, Object>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "version", "1.0.0",
            "status", "ERROR",
            "error", Map.of("errorCode", code, "message", message)
        ));
    }
}
