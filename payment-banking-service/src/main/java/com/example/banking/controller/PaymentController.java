package com.example.banking.controller;

import com.example.banking.entity.Transaction;
import com.example.banking.service.AccountService;
import com.example.banking.service.CelcoinClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AccountService service;
    private final CelcoinClient celcoin;

    public PaymentController(AccountService service, CelcoinClient celcoin) {
        this.service = service;
        this.celcoin = celcoin;
    }

    public record PixRequest(String accountNumber, String transactionPassword,
                              String pixKey, double amount, String description) {}

    @PostMapping("/pix")
    public ResponseEntity<?> payPix(@RequestBody PixRequest req) {
        try {
            Transaction tx = service.payPix(
                req.accountNumber(), req.transactionPassword(),
                req.pixKey(), BigDecimal.valueOf(req.amount()), req.description()
            );
            return ResponseEntity.ok(Map.of(
                "message", String.format("Pix de R$ %.2f enviado para %s com sucesso!", req.amount(), req.pixKey()),
                "transactionId", tx.getId(),
                "balanceAfter", tx.getBalanceAfter()
            ));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pix/lookup/{key}")
    public ResponseEntity<?> lookupPixKey(@PathVariable String key) {
        try {
            java.util.Map<String, Object> info = service.lookupPixKeyInfo(key);
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record QrCodeRequest(String emv) {}

    /** Decodes a Pix EMV (QR Code string) and returns recipient info + amount for confirmation. */
    @PostMapping("/pix/qrcode/decode")
    public ResponseEntity<?> decodeQrCode(@RequestBody QrCodeRequest req) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = celcoin.post("/pix/v1/emv/decode", Map.of("emv", req.emv()));
            if (resp.containsKey("error")) {
                return ResponseEntity.badRequest().body(resp);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) resp.get("body");
            if (body == null) return ResponseEntity.badRequest().body(Map.of("error", "Resposta inválida do decoder EMV"));

            // Enrich with DICT lookup for recipient name/bank
            String pixKey = (String) body.get("key");
            if (pixKey != null && !pixKey.isBlank()) {
                try {
                    Map<String, Object> dictInfo = service.lookupPixKeyInfo(pixKey);
                    body = new java.util.HashMap<>(body);
                    body.put("dictInfo", dictInfo);
                } catch (Exception ignored) { /* dict lookup failure is non-fatal */ }
            }
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record BoletoRequest(String accountNumber, String transactionPassword, String boletoCode) {}

    @PostMapping("/boleto")
    public ResponseEntity<?> payBoleto(@RequestBody BoletoRequest req) {
        try {
            Transaction tx = service.payBoleto(
                req.accountNumber(), req.transactionPassword(), req.boletoCode()
            );
            return ResponseEntity.ok(Map.of(
                "message", String.format("Boleto pago no valor de R$ %.2f com sucesso!", tx.getAmount()),
                "transactionId", tx.getId(),
                "balanceAfter", tx.getBalanceAfter()
            ));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
