package com.example.banking.service;

import com.example.banking.entity.Account;
import com.example.banking.entity.Transaction;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;
    private final CelcoinClient celcoin;
    private final BCryptPasswordEncoder encoder;

    public AccountService(AccountRepository accountRepo, TransactionRepository txRepo, CelcoinClient celcoin, BCryptPasswordEncoder encoder) {
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.celcoin = celcoin;
        this.encoder = encoder;
    }

    /**
     * Opens a new account via Celcoin onboarding API.
     * Polls until CONFIRMED (max 30 seconds), then stores locally with hashed transaction password.
     */
    @Transactional
    public Account openAccount(String name, String cpf, String email,
                               String phoneNumber, String motherName, String birthDate,
                               String transactionPassword) {
        if (accountRepo.existsByCpf(cpf.replaceAll("\\D", ""))) {
            throw new IllegalArgumentException("CPF já possui conta cadastrada.");
        }

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

    public BigDecimal getBalance(String accountNumber, String transactionPassword) {
        Account account = findAndAuthenticate(accountNumber, transactionPassword);
        return account.getBalance();
    }

    public List<Transaction> getStatement(String accountNumber, String transactionPassword, int days) {
        findAndAuthenticate(accountNumber, transactionPassword);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return txRepo.findByAccountNumberAndCreatedAtAfterOrderByCreatedAtDesc(accountNumber, since);
    }

    @Transactional
    public Transaction payPix(String accountNumber, String transactionPassword,
                               String pixKey, BigDecimal amount, String description) {
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
        tx.setDescription(description != null && !description.isBlank() ? description : "Pix para " + pixKey);
        return txRepo.save(tx);
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
