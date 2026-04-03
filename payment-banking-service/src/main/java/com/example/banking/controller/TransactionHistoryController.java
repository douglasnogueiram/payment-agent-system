package com.example.banking.controller;

import com.example.banking.entity.Transaction;
import com.example.banking.repository.TransactionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionHistoryController {

    private final TransactionRepository repository;

    public TransactionHistoryController(TransactionRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/transactions")
    public List<Transaction> getAllTransactions() {
        return repository.findAll();
    }

    @GetMapping("/accounts/{accountNumber}/transactions")
    public List<Transaction> getByAccount(@PathVariable String accountNumber) {
        return repository.findByAccountNumberOrderByCreatedAtDesc(accountNumber);
    }
}
