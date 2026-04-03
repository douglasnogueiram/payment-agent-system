package com.example.banking.controller;

import com.example.banking.entity.Account;
import com.example.banking.entity.Transaction;
import com.example.banking.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    public record OpenAccountRequest(String name, String cpf, String email, String transactionPassword) {}

    @PostMapping
    public ResponseEntity<?> openAccount(@RequestBody OpenAccountRequest req) {
        try {
            Account account = service.openAccount(req.name(), req.cpf(), req.email(), req.transactionPassword());
            return ResponseEntity.ok(Map.of(
                "message", "Conta aberta com sucesso!",
                "accountNumber", account.getAccountNumber(),
                "agency", account.getAgency(),
                "name", account.getName()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record AuthRequest(String accountNumber, String transactionPassword) {}

    @PostMapping("/balance")
    public ResponseEntity<?> getBalance(@RequestBody AuthRequest req) {
        try {
            BigDecimal balance = service.getBalance(req.accountNumber(), req.transactionPassword());
            return ResponseEntity.ok(Map.of(
                "accountNumber", req.accountNumber(),
                "balance", balance,
                "message", "Saldo atual: R$ " + balance
            ));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record StatementRequest(String accountNumber, String transactionPassword, int days) {}

    @PostMapping("/statement")
    public ResponseEntity<?> getStatement(@RequestBody StatementRequest req) {
        try {
            List<Transaction> transactions = service.getStatement(
                req.accountNumber(), req.transactionPassword(), req.days());
            return ResponseEntity.ok(Map.of(
                "accountNumber", req.accountNumber(),
                "days", req.days(),
                "transactions", transactions
            ));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

