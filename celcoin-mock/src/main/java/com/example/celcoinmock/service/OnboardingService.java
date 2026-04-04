package com.example.celcoinmock.service;

import com.example.celcoinmock.entity.OnboardingRecord;
import com.example.celcoinmock.repository.OnboardingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class OnboardingService {

    private final OnboardingRepository repository;
    private final long onboardingDelayMs;

    public OnboardingService(
            OnboardingRepository repository,
            @Value("${celcoin.mock.onboarding-delay-ms:3000}") long onboardingDelayMs) {
        this.repository = repository;
        this.onboardingDelayMs = onboardingDelayMs;
    }

    @Transactional
    public OnboardingRecord create(OnboardingRecord record) {
        if (repository.existsByDocumentNumber(record.getDocumentNumber())) {
            throw new IllegalArgumentException("CBE022|Já existe uma conta vinculada a este CPF.");
        }
        if (repository.existsByClientCode(record.getClientCode())) {
            throw new IllegalArgumentException("CBE001|ClientCode já utilizado.");
        }
        record.setOnboardingId(UUID.randomUUID().toString());
        record.setStatus(OnboardingRecord.OnboardingStatus.PROCESSING);
        return repository.save(record);
    }

    public OnboardingRecord findByOnboardingId(String onboardingId) {
        return repository.findById(onboardingId)
                .orElseThrow(() -> new IllegalArgumentException("onboardingId não encontrado: " + onboardingId));
    }

    /**
     * Simulates async processing: moves PROCESSING records to CONFIRMED after the configured delay.
     * Runs every 2 seconds.
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOnboardingQueue() {
        Instant cutoff = Instant.now().minusMillis(onboardingDelayMs);
        List<OnboardingRecord> pending = repository.findByStatus(OnboardingRecord.OnboardingStatus.PROCESSING);
        for (OnboardingRecord record : pending) {
            if (record.getCreatedAt().isBefore(cutoff)) {
                record.setStatus(OnboardingRecord.OnboardingStatus.CONFIRMED);
                record.setAccountBranch("0001");
                record.setAccountNumber(generateAccountNumber());
                record.setConfirmedAt(Instant.now());
                repository.save(record);
            }
        }
    }

    private String generateAccountNumber() {
        String candidate;
        do {
            candidate = String.format("%011d", new Random().nextLong(100_000_000_000L));
            final String check = candidate;
            if (repository.findAll().stream().noneMatch(r -> check.equals(r.getAccountNumber()))) {
                return candidate;
            }
        } while (true);
    }
}
