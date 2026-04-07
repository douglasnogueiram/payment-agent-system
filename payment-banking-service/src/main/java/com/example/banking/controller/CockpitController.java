package com.example.banking.controller;

import com.example.banking.entity.Account;
import com.example.banking.entity.Transaction;
import com.example.banking.entity.TransactionEvent;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionEventRepository;
import com.example.banking.repository.TransactionRepository;
import com.example.banking.service.PdfReceiptService;
import com.example.banking.service.PdfStatementStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Read-only cockpit endpoints — no authentication required.
 * Exposed at /api/admin/* and proxied directly from the frontend via /banking/.
 */
@RestController
@RequestMapping("/api/admin")
public class CockpitController {

    private final TransactionRepository     txRepo;
    private final TransactionEventRepository eventRepo;
    private final AccountRepository         accountRepo;
    private final PdfReceiptService         receiptService;
    private final PdfStatementStore         pdfStore;

    public CockpitController(TransactionRepository txRepo,
                             TransactionEventRepository eventRepo,
                             AccountRepository accountRepo,
                             PdfReceiptService receiptService,
                             PdfStatementStore pdfStore) {
        this.txRepo         = txRepo;
        this.eventRepo      = eventRepo;
        this.accountRepo    = accountRepo;
        this.receiptService = receiptService;
        this.pdfStore       = pdfStore;
    }

    /** Returns the 30 most recent PIX/BOLETO transactions (all accounts). */
    @GetMapping("/transactions")
    public ResponseEntity<List<Map<String, Object>>> recentTransactions() {
        List<Transaction> txs = txRepo.findTop30ByTypeNotOrderByCreatedAtDesc(
            Transaction.TransactionType.ACCOUNT_CREDIT
        );
        return ResponseEntity.ok(txs.stream().map(this::toMap).toList());
    }

    /** Returns all events for a given transaction, oldest first. */
    @GetMapping("/transactions/{id}/events")
    public ResponseEntity<List<Map<String, Object>>> transactionEvents(@PathVariable Long id) {
        List<TransactionEvent> events = eventRepo.findByTransactionIdOrderByCreatedAtAsc(id);
        return ResponseEntity.ok(events.stream().map(this::eventToMap).toList());
    }

    /**
     * Generates a Pix receipt PDF for a completed transaction and returns a
     * short-lived download URL (valid 10 minutes). No authentication required
     * (same policy as the rest of this admin controller).
     */
    @GetMapping("/transactions/{id}/receipt/pdf")
    public ResponseEntity<?> generateReceipt(@PathVariable Long id) {
        Transaction tx = txRepo.findById(id).orElse(null);
        if (tx == null) {
            return ResponseEntity.notFound().build();
        }
        if (tx.getStatus() == Transaction.TransactionStatus.PENDING) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Transação ainda pendente — aguarde a confirmação."));
        }
        Account payer = accountRepo.findByAccountNumber(tx.getAccountNumber()).orElse(null);
        if (payer == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Conta pagadora não encontrada."));
        }

        byte[] pdfBytes = receiptService.generate(payer, tx);
        String token = pdfStore.store(pdfBytes);
        return ResponseEntity.ok(Map.of(
                "downloadUrl",      "/banking/api/admin/receipt/pdf/" + token,
                "expiresInSeconds", 600
        ));
    }

    /** Streams the receipt PDF by token. */
    @GetMapping("/receipt/pdf/{token}")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable String token) {
        return pdfStore.get(token)
                .map(bytes -> {
                    String filename = "comprovante_pix_" +
                            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + filename + "\"")
                            .body(bytes);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toMap(Transaction tx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                   tx.getId());
        m.put("accountNumber",        tx.getAccountNumber());
        m.put("type",                 tx.getType().name());
        m.put("amount",               tx.getAmount());
        m.put("status",               tx.getStatus().name());
        m.put("reference",            tx.getReference());
        m.put("description",          tx.getDescription());
        m.put("balanceAfter",         tx.getBalanceAfter());
        m.put("endToEndId",           tx.getEndToEndId());
        m.put("celcoinTransactionId", tx.getCelcoinTransactionId());
        m.put("recipientName",        tx.getRecipientName());
        m.put("createdAt",            tx.getCreatedAt());
        return m;
    }

    private Map<String, Object> eventToMap(TransactionEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",            e.getId());
        m.put("transactionId", e.getTransactionId());
        m.put("eventType",     e.getEventType());
        m.put("serviceName",   e.getServiceName());
        m.put("message",       e.getMessage());
        m.put("createdAt",     e.getCreatedAt());
        return m;
    }
}
