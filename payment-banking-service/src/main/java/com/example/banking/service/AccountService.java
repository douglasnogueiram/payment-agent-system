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
import java.util.Random;

@Service
public class AccountService {

    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AccountService(AccountRepository accountRepo, TransactionRepository txRepo) {
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
    }

    @Transactional
    public Account openAccount(String name, String cpf, String email, String transactionPassword) {
        if (accountRepo.existsByCpf(cpf)) {
            throw new IllegalArgumentException("CPF já possui conta cadastrada: " + cpf);
        }
        Account account = new Account();
        account.setName(name);
        account.setCpf(cpf);
        account.setEmail(email);
        account.setAccountNumber(generateAccountNumber());
        account.setPasswordHash(encoder.encode(transactionPassword));
        // Welcome credit of R$0.00 — real BaaS would handle this
        account.setBalance(BigDecimal.ZERO);
        Account saved = accountRepo.save(account);

        // Record account creation as first transaction
        Transaction tx = new Transaction();
        tx.setAccountNumber(saved.getAccountNumber());
        tx.setType(Transaction.TransactionType.ACCOUNT_CREDIT);
        tx.setAmount(BigDecimal.ZERO);
        tx.setBalanceAfter(BigDecimal.ZERO);
        tx.setDescription("Abertura de conta corrente");
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
    public Transaction payBoleto(String accountNumber, String transactionPassword,
                                  String boletoCode) {
        // In a real BaaS, amount would come from barcode parsing.
        // Mock: extract amount from last 10 digits of barcode (simplified).
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

    private String generateAccountNumber() {
        String candidate;
        do {
            candidate = String.format("%08d", new Random().nextInt(100_000_000));
        } while (accountRepo.findByAccountNumber(candidate).isPresent());
        return candidate;
    }

    private BigDecimal parseBoletoAmount(String boletoCode) {
        // Simplified: real implementation would decode the barcode field 10
        try {
            String digits = boletoCode.replaceAll("\\D", "");
            if (digits.length() >= 10) {
                long cents = Long.parseLong(digits.substring(digits.length() - 10));
                return BigDecimal.valueOf(cents, 2);
            }
        } catch (NumberFormatException ignored) {}
        return new BigDecimal("10.00"); // fallback for mock
    }
}
