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

    public record OpenAccountRequest(
        String name,
        String cpf,
        String email,
        String phoneNumber,
        String motherName,
        String birthDate,
        String transactionPassword,
        String keycloakUserId
    ) {}

    @PostMapping
    public ResponseEntity<?> openAccount(@RequestBody OpenAccountRequest req) {
        try {
            Account account = service.openAccount(
                req.name(), req.cpf(), req.email(),
                req.phoneNumber() != null ? req.phoneNumber() : "+5511999999999",
                req.motherName() != null ? req.motherName() : "Nome da Mãe",
                req.birthDate() != null ? req.birthDate() : "01-01-1990",
                req.transactionPassword(),
                req.keycloakUserId()
            );
            return ResponseEntity.ok(Map.of(
                "message", "Conta aberta com sucesso!",
                "accountNumber", account.getAccountNumber(),
                "agency", account.getAgency(),
                "name", account.getName()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
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

    /** Internal endpoint — called by payment-agent to resolve account number from JWT sub. */
    @GetMapping("/by-user/{keycloakUserId}")
    public ResponseEntity<?> getByUser(@PathVariable String keycloakUserId) {
        return service.findByKeycloakUserId(keycloakUserId)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(Map.of(
                        "accountNumber", a.getAccountNumber(),
                        "agency", a.getAgency(),
                        "name", a.getName()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-email/{email}")
    public ResponseEntity<?> getByEmail(@PathVariable String email) {
        return service.findByEmail(email)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(Map.of(
                        "accountNumber", a.getAccountNumber(),
                        "agency", a.getAgency(),
                        "name", a.getName()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    public record LinkUserRequest(String keycloakUserId, String email) {}

    /**
     * Links a keycloakUserId to an existing account found by email.
     * Called automatically by payment-agent when an account exists but has no keycloakUserId yet.
     */
    @PostMapping("/link-user")
    public ResponseEntity<?> linkUser(@RequestBody LinkUserRequest req) {
        try {
            Map<String, Object> result = service.linkKeycloakUser(req.keycloakUserId(), req.email());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

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
