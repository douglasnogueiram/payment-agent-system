package com.example.celcoinmock.service;

import com.example.celcoinmock.dto.AddressDto;
import com.example.celcoinmock.entity.OnboardingRecord;
import com.example.celcoinmock.repository.OnboardingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Service
public class OnboardingService {

    private static final Set<String> VALID_STATES = Set.of(
        "AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG",
        "PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO"
    );

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
        // CBE015 — CPF digit validation
        if (!isValidCpf(record.getDocumentNumber())) {
            throw new IllegalArgumentException("CBE015|CPF inválido. Os dígitos verificadores não conferem.");
        }
        // CBE022 — duplicate CPF
        if (repository.existsByDocumentNumber(record.getDocumentNumber())) {
            throw new IllegalArgumentException("CBE022|Já existe uma conta vinculada a este CPF.");
        }
        // CBE001 — duplicate clientCode
        if (repository.existsByClientCode(record.getClientCode())) {
            throw new IllegalArgumentException("CBE001|ClientCode já utilizado.");
        }
        // CBE016 — minimum age 18
        validateAge(record.getBirthDate());

        record.setOnboardingId(UUID.randomUUID().toString());
        record.setStatus(OnboardingRecord.OnboardingStatus.PROCESSING);
        return repository.save(record);
    }

    public OnboardingRecord findByOnboardingId(String onboardingId) {
        return repository.findById(onboardingId)
                .orElseThrow(() -> new IllegalArgumentException("onboardingId não encontrado: " + onboardingId));
    }

    /** Validates address fields — called by OnboardingController before creating record. */
    public void validateAddress(AddressDto address) {
        if (address.postalCode() == null || address.postalCode().replaceAll("\\D", "").length() != 8) {
            throw new IllegalArgumentException("CBE010|CEP inválido. Deve conter 8 dígitos.");
        }
        if (address.street() == null || address.street().isBlank()) {
            throw new IllegalArgumentException("CBE014|Logradouro (street) é obrigatório.");
        }
        if (address.neighborhood() == null || address.neighborhood().isBlank()) {
            throw new IllegalArgumentException("CBE013|Bairro (neighborhood) é obrigatório.");
        }
        if (address.city() == null || address.city().isBlank()) {
            throw new IllegalArgumentException("CBE011|Cidade (city) é obrigatória.");
        }
        if (address.state() == null || !VALID_STATES.contains(address.state().toUpperCase())) {
            throw new IllegalArgumentException("CBE012|Estado inválido. Use a sigla de 2 letras (ex: SP, RJ).");
        }
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

    // ── helpers ──────────────────────────────────────────────────────────────

    private void validateAge(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) return;
        try {
            LocalDate dob = LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            long age = ChronoUnit.YEARS.between(dob, LocalDate.now());
            if (age < 18) {
                throw new IllegalArgumentException("CBE016|Idade mínima para abertura de conta é 18 anos.");
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("CBE008|birthDate inválido. Use o formato DD-MM-YYYY.");
        }
    }

    /** Validates CPF using the standard two-digit verifier algorithm. */
    static boolean isValidCpf(String raw) {
        if (raw == null) return false;
        String cpf = raw.replaceAll("\\D", "");
        if (cpf.length() != 11) return false;
        if (cpf.chars().distinct().count() == 1) return false; // rejects "00000000000" etc.
        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (cpf.charAt(i) - '0') * (10 - i);
        int d1 = 11 - (sum % 11); if (d1 >= 10) d1 = 0;
        sum = 0;
        for (int i = 0; i < 10; i++) sum += (cpf.charAt(i) - '0') * (11 - i);
        int d2 = 11 - (sum % 11); if (d2 >= 10) d2 = 0;
        return d1 == (cpf.charAt(9) - '0') && d2 == (cpf.charAt(10) - '0');
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
