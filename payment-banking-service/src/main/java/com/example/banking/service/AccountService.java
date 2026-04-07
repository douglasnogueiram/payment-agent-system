package com.example.banking.service;

import com.example.banking.entity.Account;
import com.example.banking.entity.Transaction;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AccountService {

    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;
    private final CelcoinClient celcoin;
    private final BCryptPasswordEncoder encoder;
    private final TransactionTemplate txTemplate;
    private final PixWebhookNotifier webhookNotifier;
    private final TransactionEventLogger eventLogger;

    public AccountService(AccountRepository accountRepo, TransactionRepository txRepo,
                          CelcoinClient celcoin, BCryptPasswordEncoder encoder,
                          TransactionTemplate txTemplate, PixWebhookNotifier webhookNotifier,
                          TransactionEventLogger eventLogger) {
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.celcoin = celcoin;
        this.encoder = encoder;
        this.txTemplate = txTemplate;
        this.webhookNotifier = webhookNotifier;
        this.eventLogger = eventLogger;
    }

    /**
     * Opens a new account via Celcoin onboarding API.
     * Polls until CONFIRMED (max 30 seconds), then stores locally with hashed transaction password.
     */
    @Transactional
    public Account openAccount(String name, String cpf, String email,
                               String phoneNumber, String motherName, String birthDate,
                               String transactionPassword, String keycloakUserId) {
        if (accountRepo.existsByCpf(cpf.replaceAll("\\D", ""))) {
            throw new IllegalArgumentException("CPF já possui conta cadastrada.");
        }
        if (keycloakUserId != null && !keycloakUserId.isBlank()
                && accountRepo.existsByKeycloakUserId(keycloakUserId)) {
            throw new IllegalArgumentException("Este usuário já possui conta cadastrada.");
        }
        validateTransactionPassword(transactionPassword);

        String clientCode = UUID.randomUUID().toString();

        // Call Celcoin: create account
        Map<String, Object> createResp = celcoin.post(
            "/baas/v2/account/natural-person/create",
            Map.of(
                "clientCode", clientCode,
                "accountOnboardingType", "BANKACCOUNT",
                "documentNumber", cpf.replaceAll("\\D", ""),
                "phoneNumber", phoneNumber,
                "email", email,
                "motherName", motherName,
                "fullName", name,
                "birthDate", birthDate,
                "address", Map.of(
                    "postalCode", "01310100",
                    "street", "Av. Paulista",
                    "number", "1000",
                    "neighborhood", "Bela Vista",
                    "city", "São Paulo",
                    "state", "SP"
                ),
                "isPoliticallyExposedPerson", false
            )
        );

        if (!"PROCESSING".equals(createResp.get("status"))) {
            Object error = createResp.get("error");
            String msg = error != null ? error.toString() : "Erro ao criar conta na Celcoin.";
            throw new IllegalStateException(msg);
        }

        @SuppressWarnings("unchecked")
        String onboardingId = (String) ((Map<String, Object>) createResp.get("body")).get("onBoardingId");

        // Poll until CONFIRMED (max 30s, every 2s)
        Map<String, Object> statusResp = pollUntilConfirmed(onboardingId, 15, 2000);

        @SuppressWarnings("unchecked")
        Map<String, Object> accountData = (Map<String, Object>) ((Map<String, Object>) statusResp.get("body")).get("account");

        String accountNumber = (String) accountData.get("account");
        String branch = (String) accountData.get("branch");

        Account account = new Account();
        account.setName(name);
        account.setCpf(cpf.replaceAll("\\D", ""));
        account.setEmail(email);
        account.setAccountNumber(accountNumber);
        account.setAgency(branch);
        account.setBalance(BigDecimal.ZERO);
        account.setPasswordHash(encoder.encode(transactionPassword));
        account.setCelcoinOnboardingId(onboardingId);
        if (keycloakUserId != null && !keycloakUserId.isBlank()) {
            account.setKeycloakUserId(keycloakUserId);
        }
        Account saved = accountRepo.save(account);

        // Record opening transaction
        Transaction tx = new Transaction();
        tx.setAccountNumber(accountNumber);
        tx.setType(Transaction.TransactionType.ACCOUNT_CREDIT);
        tx.setAmount(BigDecimal.ZERO);
        tx.setBalanceAfter(BigDecimal.ZERO);
        tx.setDescription("Abertura de conta corrente via Celcoin");
        txRepo.save(tx);

        return saved;
    }

    public Optional<Account> findByKeycloakUserId(String keycloakUserId) {
        return accountRepo.findByKeycloakUserId(keycloakUserId);
    }

    public Optional<Account> findByEmail(String email) {
        return accountRepo.findByEmail(email);
    }

    @Transactional
    public Map<String, Object> linkKeycloakUser(String keycloakUserId, String email) {
        if (keycloakUserId == null || keycloakUserId.isBlank())
            throw new IllegalArgumentException("keycloakUserId obrigatório.");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("email obrigatório.");
        if (accountRepo.existsByKeycloakUserId(keycloakUserId))
            throw new IllegalStateException("keycloakUserId já vinculado a outra conta.");
        Account account = accountRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Nenhuma conta encontrada para o e-mail: " + email));
        if (account.getKeycloakUserId() != null && !account.getKeycloakUserId().isBlank())
            throw new IllegalStateException("Esta conta já está vinculada a outro usuário Keycloak.");
        account.setKeycloakUserId(keycloakUserId);
        accountRepo.save(account);
        return Map.of("accountNumber", account.getAccountNumber(), "agency", account.getAgency(), "name", account.getName());
    }

    public BigDecimal getBalance(String accountNumber, String transactionPassword) {
        Account account = findAndAuthenticate(accountNumber, transactionPassword);
        return account.getBalance();
    }

    public List<Transaction> getStatement(String accountNumber, String transactionPassword, int days) {
        findAndAuthenticate(accountNumber, transactionPassword);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return txRepo.findByAccountNumberAndCreatedAtAfterOrderByCreatedAtDesc(accountNumber, since);
    }

    /**
     * Looks up a PIX key in Celcoin DICT and returns recipient info + endToEndId.
     * Throws IllegalArgumentException if key is not found.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> lookupPixKeyInfo(String pixKey) {
        try {
            Map<String, Object> resp = celcoin.get("/celcoin/pix/v2/dict/key/" + pixKey);
            if (!"SUCCESS".equals(resp.get("status"))) {
                Map<String, Object> error = (Map<String, Object>) resp.get("error");
                String msg = error != null ? (String) error.get("message") : "Chave Pix não encontrada.";
                throw new IllegalArgumentException(msg);
            }
            return (Map<String, Object>) resp.get("body");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> body = om.readValue(e.getResponseBodyAsString(), Map.class);
                Map<String, Object> error = (Map<String, Object>) body.get("error");
                String msg = error != null ? (String) error.get("message") : "Chave Pix não encontrada no DICT.";
                throw new IllegalArgumentException(msg);
            } catch (IllegalArgumentException iae) {
                throw iae;
            } catch (Exception ignored) {
                throw new IllegalArgumentException("Chave Pix não encontrada no DICT.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Não foi possível consultar o DICT. Tente novamente.");
        }
    }

    /**
     * Full PIX payment flow — blocks until Celcoin webhook confirms (or timeout).
     *
     * Step 1 (outside tx): DICT lookup + pre-register webhook future
     * Step 2 (transactional): authenticate + debit + create PENDING tx + submit to Celcoin
     * Step 3 (block):        wait for webhook CompletableFuture (max 15s)
     * Step 4 (transactional): finalize status → SUCCESS or FAILED (with balance revert)
     *
     * Using TransactionTemplate instead of @Transactional so DB locks are released
     * before we block waiting for the webhook, avoiding deadlock with the webhook handler.
     */
    public Transaction payPix(String accountNumber, String transactionPassword,
                               String pixKey, BigDecimal amount, String description) {

        // Step 1: DICT lookup — validates key, obtains endToEndId (no DB transaction)
        eventLogger.log(null, "DICT_LOOKUP", "Consultando DICT para chave Pix: " + pixKey);
        Map<String, Object> dictBody = lookupPixKeyInfo(pixKey);
        String endToEndId = (String) dictBody.get("endToEndId");

        @SuppressWarnings("unchecked")
        Map<String, Object> owner = (Map<String, Object>) dictBody.get("owner");
        @SuppressWarnings("unchecked")
        Map<String, Object> dictAccount = (Map<String, Object>) dictBody.get("account");
        String ownerName = owner != null ? (String) owner.get("name") : pixKey;
        String ispb      = dictAccount != null ? (String) dictAccount.get("participant") : "";
        eventLogger.log(null, "DICT_FOUND",
            String.format("Destinatário: %s | ISPB: %s | E2E: %s", ownerName, ispb, endToEndId));

        // Pre-register webhook future BEFORE calling Celcoin (prevents race condition)
        CompletableFuture<String> webhookFuture = webhookNotifier.register(endToEndId);

        // Step 2: Transactional — debit balance + PENDING tx + Celcoin transfer
        Transaction saved;
        try {
            saved = txTemplate.execute(txStatus -> {
                Account account = findAndAuthenticate(accountNumber, transactionPassword);
                if (account.getBalance().compareTo(amount) < 0) {
                    throw new IllegalArgumentException("Saldo insuficiente para realizar o Pix.");
                }
                account.setBalance(account.getBalance().subtract(amount));
                accountRepo.save(account);

                Transaction tx = new Transaction();
                tx.setAccountNumber(accountNumber);
                tx.setType(Transaction.TransactionType.PIX_OUT);
                tx.setAmount(amount);
                tx.setBalanceAfter(account.getBalance());
                tx.setReference(pixKey);
                tx.setEndToEndId(endToEndId);
                tx.setDescription(description != null && !description.isBlank() ? description : "Pix para " + pixKey);
                tx.setRecipientName(ownerName);
                tx.setStatus(Transaction.TransactionStatus.PENDING);
                Transaction pending = txRepo.save(tx);
                eventLogger.log(pending.getId(), "BALANCE_DEBITED",
                    String.format("R$ %.2f debitado. Transação #%d criada como PENDING", amount, pending.getId()));

                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resp = celcoin.post("/pix/v2/transfer", Map.of(
                        "clientCode", UUID.randomUUID().toString(),
                        "endToEndId", endToEndId != null ? endToEndId : "",
                        "pixAlias", pixKey,
                        "amount", amount,
                        "description", description != null ? description : "",
                        "payer", Map.of(
                            "documentNumber", account.getCpf(),
                            "name", account.getName(),
                            "branch", account.getAgency() != null ? account.getAgency() : "0001",
                            "accountNumber", accountNumber,
                            "accountType", "CACC"
                        )
                    ));
                    if ("PROCESSING".equals(resp.get("status"))) {
                        Map<String, Object> body = (Map<String, Object>) resp.get("body");
                        String celcoinTxId = (String) body.get("transactionId");
                        pending.setCelcoinTransactionId(celcoinTxId);
                        txRepo.save(pending);
                        webhookNotifier.mapTxId(celcoinTxId, endToEndId);
                        eventLogger.log(pending.getId(), "CELCOIN_SUBMITTED",
                            "Transferência submetida à Celcoin. TxId: " + celcoinTxId);
                    }
                } catch (Exception e) {
                    account.setBalance(account.getBalance().add(amount));
                    accountRepo.save(account);
                    pending.setStatus(Transaction.TransactionStatus.FAILED);
                    txRepo.save(pending);
                    throw new RuntimeException("Erro ao submeter Pix à Celcoin: " + e.getMessage(), e);
                }
                return pending;
            });
        } catch (IllegalArgumentException | SecurityException e) {
            webhookNotifier.cleanup(endToEndId);
            webhookFuture.cancel(true);
            throw e;
        } catch (Exception e) {
            webhookNotifier.cleanup(endToEndId);
            webhookFuture.cancel(true);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException(cause.getMessage());
        }

        // Step 3: Block until webhook arrives (DB transaction already committed above)
        eventLogger.log(saved.getId(), "AWAITING_WEBHOOK", "Aguardando confirmação da Celcoin...");
        final Transaction pendingTx = saved;
        try {
            String webhookStatus = webhookFuture.get(15, TimeUnit.SECONDS);

            // Step 4: Transactional — finalize status
            return txTemplate.execute(txStatus -> {
                Transaction tx = txRepo.findById(pendingTx.getId()).orElseThrow();
                if (tx.getStatus() == Transaction.TransactionStatus.PENDING) {
                    if ("PAID".equals(webhookStatus) || "SUCCESS".equals(webhookStatus)) {
                        tx.setStatus(Transaction.TransactionStatus.SUCCESS);
                        eventLogger.log(tx.getId(), "FINALIZED", "Transação finalizada: SUCCESS");
                    } else {
                        tx.setStatus(Transaction.TransactionStatus.FAILED);
                        accountRepo.findByAccountNumber(accountNumber).ifPresent(acc -> {
                            acc.setBalance(acc.getBalance().add(amount));
                            accountRepo.save(acc);
                        });
                        eventLogger.log(tx.getId(), "FINALIZED", "Transação finalizada: FAILED. Saldo estornado.");
                    }
                    txRepo.save(tx);
                }
                return tx;
            });

        } catch (TimeoutException e) {
            webhookNotifier.cleanup(endToEndId);
            return pendingTx; // Webhook didn't arrive — return PENDING
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            webhookNotifier.cleanup(endToEndId);
            return pendingTx;
        } catch (java.util.concurrent.ExecutionException e) {
            webhookNotifier.cleanup(endToEndId);
            return pendingTx;
        }
    }

    /**
     * Called by webhook from Celcoin when PIX transfer status changes.
     * Updates transaction status; reverts balance if FAILED.
     */
    @Transactional
    public void updatePixTransactionStatus(String celcoinTransactionId, String endToEndId, String status) {
        Transaction tx = null;
        if (celcoinTransactionId != null && !celcoinTransactionId.isBlank()) {
            tx = txRepo.findByCelcoinTransactionId(celcoinTransactionId).orElse(null);
        }
        if (tx == null && endToEndId != null && !endToEndId.isBlank()) {
            tx = txRepo.findByEndToEndId(endToEndId).orElse(null);
        }
        if (tx == null || tx.getStatus() != Transaction.TransactionStatus.PENDING) return;

        final Transaction finalTx = tx;
        if ("PAID".equals(status) || "SUCCESS".equals(status)) {
            finalTx.setStatus(Transaction.TransactionStatus.SUCCESS);
            txRepo.save(finalTx);
        } else if ("FAILED".equals(status) || "ERROR".equals(status)) {
            finalTx.setStatus(Transaction.TransactionStatus.FAILED);
            // Revert balance
            accountRepo.findByAccountNumber(finalTx.getAccountNumber()).ifPresent(account -> {
                account.setBalance(account.getBalance().add(finalTx.getAmount()));
                accountRepo.save(account);
            });
            txRepo.save(finalTx);
        }
    }

    @Transactional
    public Transaction payBoleto(String accountNumber, String transactionPassword, String boletoCode) {
        BigDecimal amount = parseBoletoAmount(boletoCode);
        Account account = findAndAuthenticate(accountNumber, transactionPassword);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente para pagar o boleto.");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepo.save(account);

        Transaction tx = new Transaction();
        tx.setAccountNumber(accountNumber);
        tx.setType(Transaction.TransactionType.BOLETO_OUT);
        tx.setAmount(amount);
        tx.setBalanceAfter(account.getBalance());
        tx.setReference(boletoCode);
        tx.setDescription("Pagamento de boleto");
        return txRepo.save(tx);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void validateTransactionPassword(String password) {
        if (password == null || !password.matches("\\d{6}")) {
            throw new IllegalArgumentException("Senha de transação deve ter exatamente 6 dígitos numéricos.");
        }
    }

    private Account findAndAuthenticate(String accountNumber, String transactionPassword) {
        Account account = accountRepo.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada: " + accountNumber));
        if (!encoder.matches(transactionPassword, account.getPasswordHash())) {
            throw new SecurityException("Senha de transação incorreta.");
        }
        return account;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pollUntilConfirmed(String onboardingId, int maxAttempts, long delayMs) {
        for (int i = 0; i < maxAttempts; i++) {
            Map<String, Object> resp = celcoin.get(
                "/baas-onboarding/v1/account/check?onboardingId=" + onboardingId
            );
            String status = (String) resp.get("status");
            if ("CONFIRMED".equals(status)) return resp;
            if ("ERROR".equals(status)) {
                throw new IllegalStateException("Celcoin retornou ERROR no onboarding: " + resp.get("error"));
            }
            try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new IllegalStateException("Timeout aguardando confirmação de conta na Celcoin.");
    }

    private BigDecimal parseBoletoAmount(String boletoCode) {
        try {
            String digits = boletoCode.replaceAll("\\D", "");
            if (digits.length() >= 10) {
                long cents = Long.parseLong(digits.substring(digits.length() - 10));
                return BigDecimal.valueOf(cents, 2);
            }
        } catch (NumberFormatException ignored) {}
        return new BigDecimal("10.00");
    }
}
