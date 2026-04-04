package com.example.celcoinmock.controller;

import com.example.celcoinmock.dto.*;
import com.example.celcoinmock.entity.OnboardingRecord;
import com.example.celcoinmock.service.OnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
public class OnboardingController {

    private final OnboardingService service;
    private static final DateTimeFormatter ISO = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
            .withZone(ZoneOffset.UTC);

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    /**
     * POST /baas/v2/account/natural-person/create
     * Celcoin Criar Conta PF
     */
    @PostMapping("/baas/v2/account/natural-person/create")
    public ResponseEntity<Map<String, Object>> createAccount(
            @RequestBody CreateAccountRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        // Validate required fields
        if (req.clientCode() == null || req.clientCode().isBlank()) {
            return badRequest("CBE001", "ClientCode é obrigatório.");
        }
        if (req.documentNumber() == null || req.documentNumber().isBlank()) {
            return badRequest("CBE003", "documentNumber é obrigatório e deve ser um CPF válido.");
        }
        if (req.fullName() == null || req.fullName().isBlank()) {
            return badRequest("CBE007", "fullName é obrigatório e deve ser completo.");
        }
        if (req.motherName() == null || req.motherName().isBlank()) {
            return badRequest("CBE006", "motherName é obrigatório e deve ser completo.");
        }
        if (req.email() == null || req.email().isBlank()) {
            return badRequest("CBE005", "email é obrigatório e deve ser um email válido.");
        }
        if (req.phoneNumber() == null || req.phoneNumber().isBlank()) {
            return badRequest("CBE004", "phoneNumber é obrigatório e deve ser um telefone válido.");
        }
        if (req.birthDate() == null || req.birthDate().isBlank()) {
            return badRequest("CBE008", "birthDate é obrigatório e deve ser no formato (DD-MM-YYYY).");
        }
        if (req.address() == null) {
            return badRequest("CBE009", "address é obrigatório.");
        }

        try {
            OnboardingRecord record = new OnboardingRecord();
            record.setClientCode(req.clientCode());
            record.setDocumentNumber(req.documentNumber().replaceAll("\\D", ""));
            record.setFullName(req.fullName());
            record.setSocialName(req.socialName());
            record.setEmail(req.email());
            record.setPhoneNumber(req.phoneNumber());
            record.setMotherName(req.motherName());
            record.setBirthDate(req.birthDate());

            if (req.address() != null) {
                record.setPostalCode(req.address().postalCode());
                record.setStreet(req.address().street());
                record.setAddressNumber(req.address().number());
                record.setAddressComplement(req.address().addressComplement());
                record.setNeighborhood(req.address().neighborhood());
                record.setCity(req.address().city());
                record.setState(req.address().state());
            }

            OnboardingRecord saved = service.create(record);

            return ResponseEntity.ok(Map.of(
                "version", "1.0.0",
                "status", "PROCESSING",
                "body", Map.of("onBoardingId", saved.getOnboardingId())
            ));

        } catch (IllegalArgumentException e) {
            String[] parts = e.getMessage().split("\\|", 2);
            return badRequest(parts[0], parts.length > 1 ? parts[1] : e.getMessage());
        }
    }

    /**
     * GET /baas-onboarding/v1/account/check?onboardingId=xxx
     * Consultar status da conta
     */
    @GetMapping("/baas-onboarding/v1/account/check")
    public ResponseEntity<Map<String, Object>> checkStatus(
            @RequestParam String onboardingId
    ) {
        try {
            OnboardingRecord record = service.findByOnboardingId(onboardingId);
            String statusStr = record.getStatus().name();

            var bodyBuilder = new java.util.LinkedHashMap<String, Object>();
            bodyBuilder.put("onboardingId", record.getOnboardingId());
            bodyBuilder.put("clientCode", record.getClientCode());
            bodyBuilder.put("createDate", ISO.format(record.getCreatedAt()));
            bodyBuilder.put("entity", "account-create");

            if (record.getStatus() == OnboardingRecord.OnboardingStatus.CONFIRMED) {
                bodyBuilder.put("account", Map.of(
                    "branch", record.getAccountBranch(),
                    "account", record.getAccountNumber(),
                    "name", record.getFullName(),
                    "documentNumber", record.getDocumentNumber()
                ));
            }

            return ResponseEntity.ok(Map.of(
                "version", "1.0.0",
                "status", statusStr,
                "body", bodyBuilder
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "version", "1.0.0",
                "status", "ERROR",
                "error", Map.of("errorCode", "CIE999", "message", e.getMessage())
            ));
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String code, String message) {
        return ResponseEntity.badRequest().body(Map.of(
            "version", "1.0.0",
            "status", "ERROR",
            "error", Map.of("errorCode", code, "message", message)
        ));
    }
}
