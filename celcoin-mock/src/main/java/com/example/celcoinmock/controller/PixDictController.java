package com.example.celcoinmock.controller;

import com.example.celcoinmock.entity.PixKey;
import com.example.celcoinmock.repository.PixKeyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Simulates Celcoin DICT (Diretório de Identificadores de Transações do PIX) endpoints.
 * GET /celcoin/pix/v2/dict/key/{key}  — external key lookup
 *
 * Only seeded keys return SUCCESS; all others return PIE001 (key not found).
 */
@RestController
@RequestMapping("/celcoin/pix/v2/dict")
public class PixDictController {

    private final PixKeyRepository pixKeyRepo;

    public PixDictController(PixKeyRepository pixKeyRepo) {
        this.pixKeyRepo = pixKeyRepo;
    }

    @GetMapping("/key/{key}")
    public ResponseEntity<Map<String, Object>> lookupKey(
            @PathVariable String key,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized();
        }

        Optional<PixKey> found = pixKeyRepo.findByKeyValue(key);

        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "version", "1.0.0",
                "status", "ERROR",
                "error", Map.of(
                    "errorCode", "PIE001",
                    "message", "Chave Pix não encontrada no DICT."
                )
            ));
        }

        PixKey pk = found.get();
        String endToEndId = generateEndToEndId(pk.getParticipantIspb());

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("participant", pk.getParticipantIspb());
        account.put("branch", pk.getBranch());
        account.put("accountNumber", pk.getAccountNumber());
        account.put("accountType", pk.getAccountType());
        account.put("openingDate", LocalDate.now().minusYears(2).format(DateTimeFormatter.ISO_DATE));

        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("type", pk.getOwnerType());
        owner.put("documentNumber", pk.getOwnerDocument());
        owner.put("name", pk.getOwnerName());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", pk.getKeyValue());
        body.put("keyType", pk.getKeyType());
        body.put("account", account);
        body.put("owner", owner);
        body.put("endToEndId", endToEndId);
        body.put("creationDate", LocalDate.now().minusYears(1).format(DateTimeFormatter.ISO_DATE));

        return ResponseEntity.ok(Map.of(
            "version", "1.0.0",
            "status", "SUCCESS",
            "body", body
        ));
    }

    private String generateEndToEndId(String ispb) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = UUID.randomUUID().toString().replace("-", "").substring(0, 11).toUpperCase();
        return "E" + ispb + date + "0000" + seq;
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(401).body(Map.of(
            "version", "1.0.0",
            "status", "ERROR",
            "error", Map.of("errorCode", "PIE401", "message", "Token de autenticação ausente ou inválido.")
        ));
    }
}
