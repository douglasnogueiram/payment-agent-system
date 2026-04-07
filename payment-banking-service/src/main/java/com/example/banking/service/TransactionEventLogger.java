package com.example.banking.service;

import com.example.banking.entity.TransactionEvent;
import com.example.banking.repository.TransactionEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves TransactionEvents in a dedicated transaction (REQUIRES_NEW) so they are
 * always committed independently of the calling transaction.
 */
@Component
public class TransactionEventLogger {

    private final TransactionEventRepository repo;

    public TransactionEventLogger(TransactionEventRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long transactionId, String eventType, String message) {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(transactionId);
        event.setEventType(eventType);
        event.setServiceName("banking-service");
        event.setMessage(message);
        repo.save(event);
    }
}
