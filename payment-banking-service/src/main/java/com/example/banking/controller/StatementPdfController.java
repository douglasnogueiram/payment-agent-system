package com.example.banking.controller;

import com.example.banking.entity.Account;
import com.example.banking.entity.Transaction;
import com.example.banking.repository.AccountRepository;
import com.example.banking.service.AccountService;
import com.example.banking.service.PdfStatementService;
import com.example.banking.service.PdfStatementStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statements")
public class StatementPdfController {

    private final AccountService      accountService;
    private final AccountRepository   accountRepo;
    private final PdfStatementService pdfService;
    private final PdfStatementStore   pdfStore;

    public StatementPdfController(AccountService accountService,
                                  AccountRepository accountRepo,
                                  PdfStatementService pdfService,
                                  PdfStatementStore pdfStore) {
        this.accountService = accountService;
        this.accountRepo    = accountRepo;
        this.pdfService     = pdfService;
        this.pdfStore       = pdfStore;
    }

    /**
     * Generates a PDF bank statement for the given account.
     * Validates transaction password, builds the PDF, stores it under a
     * one-time token valid for 10 minutes and returns the download URL.
     */
    @PostMapping("/pdf")
    public ResponseEntity<Map<String, Object>> generatePdf(
            @RequestBody Map<String, Object> body) {

        String accountNumber      = (String) body.get("accountNumber");
        String transactionPassword = (String) body.get("transactionPassword");
        int    days               = body.containsKey("days")
                ? ((Number) body.get("days")).intValue() : 30;

        try {
            // Authenticate and fetch transactions (reuses AccountService validation)
            List<Transaction> transactions =
                    accountService.getStatement(accountNumber, transactionPassword, days);

            // Fetch account details for the PDF header
            Account account = accountRepo.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."));

            // Generate PDF bytes
            byte[] pdfBytes = pdfService.generate(account, transactions, days);

            // Store and return download token
            String token = pdfStore.store(pdfBytes);
            String downloadUrl = "/banking/api/statements/pdf/" + token;

            return ResponseEntity.ok(Map.of(
                    "downloadUrl", downloadUrl,
                    "expiresInSeconds", 600,
                    "transactions", transactions.size(),
                    "days", days
            ));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Streams the PDF for a previously generated token.
     * Returns 404 if the token is expired or unknown.
     */
    @GetMapping("/pdf/{token}")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String token) {
        return pdfStore.get(token)
                .map(bytes -> {
                    String filename = "extrato_" +
                            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + ".pdf";
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + filename + "\"")
                            .body(bytes);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
