package com.example.banking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    private String description;

    /** Destination PIX key or boleto code, depending on type */
    private String reference;

    /** Celcoin end-to-end ID (E2E) — set when a PIX transfer is initiated */
    @Column(name = "end_to_end_id")
    private String endToEndId;

    /** Celcoin internal transaction ID returned by POST /pix/v2/transfer */
    @Column(name = "celcoin_transaction_id")
    private String celcoinTransactionId;

    /** Full name of the Pix recipient, obtained from DICT lookup */
    @Column(name = "recipient_name")
    private String recipientName;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status = TransactionStatus.SUCCESS;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public enum TransactionType { ACCOUNT_CREDIT, PIX_OUT, BOLETO_OUT }
    public enum TransactionStatus { PENDING, SUCCESS, FAILED }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getEndToEndId() { return endToEndId; }
    public void setEndToEndId(String endToEndId) { this.endToEndId = endToEndId; }
    public String getCelcoinTransactionId() { return celcoinTransactionId; }
    public void setCelcoinTransactionId(String celcoinTransactionId) { this.celcoinTransactionId = celcoinTransactionId; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
