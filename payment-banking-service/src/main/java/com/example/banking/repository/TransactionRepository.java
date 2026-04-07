package com.example.banking.repository;

import com.example.banking.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountNumberAndCreatedAtAfterOrderByCreatedAtDesc(
        String accountNumber, Instant since
    );
    List<Transaction> findByAccountNumberOrderByCreatedAtDesc(String accountNumber);
    Optional<Transaction> findByEndToEndId(String endToEndId);
    Optional<Transaction> findByCelcoinTransactionId(String celcoinTransactionId);
    List<Transaction> findTop30ByTypeNotOrderByCreatedAtDesc(Transaction.TransactionType excludedType);
}
