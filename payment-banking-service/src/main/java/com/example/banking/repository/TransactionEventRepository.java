package com.example.banking.repository;

import com.example.banking.entity.TransactionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionEventRepository extends JpaRepository<TransactionEvent, Long> {
    List<TransactionEvent> findByTransactionIdOrderByCreatedAtAsc(Long transactionId);
    List<TransactionEvent> findTop100ByTransactionIdNotNullOrderByCreatedAtDesc();
}
