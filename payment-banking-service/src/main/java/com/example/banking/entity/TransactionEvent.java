package com.example.banking.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transaction_events")
public class TransactionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Foreign key to the transaction — null for events that fire before the tx is created. */
    @Column(name = "transaction_id")
    private Long transactionId;

    /**
     * Logical step in the PIX flow.
     * DICT_LOOKUP, DICT_FOUND, BALANCE_DEBITED, CELCOIN_SUBMITTED,
     * AWAITING_WEBHOOK, WEBHOOK_RECEIVED, FINALIZED, ERROR
     */
    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "service_name", nullable = false, length = 32)
    private String serviceName;

    @Column(nullable = false, length = 512)
    private String message;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getCreatedAt() { return createdAt; }
}
