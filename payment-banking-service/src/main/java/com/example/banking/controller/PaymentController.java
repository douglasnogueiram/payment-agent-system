package com.example.banking.controller;

import com.example.banking.entity.Transaction;
import com.example.banking.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AccountService service;

    public PaymentController(AccountService service) {
        this.service = service;
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
