package com.example.paymentagent.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/transactions")
public class TransactionsController {

    private final RestClient bankingClient;

    public TransactionsController(@Value("${banking.service.url}") String bankingUrl) {
        this.bankingClient = RestClient.builder().baseUrl(bankingUrl).build();
    }

    /** Proxies transaction history from banking-service for frontend display. */
    @GetMapping
    public ResponseEntity<String> getTransactions(
            @RequestParam(required = false) String accountNumber) {
        String uri = accountNumber != null
                ? "/api/accounts/" + accountNumber + "/transactions"
                : "/api/transactions";
        return ResponseEntity.ok(bankingClient.get().uri(uri).retrieve().body(String.class));
    }
}
