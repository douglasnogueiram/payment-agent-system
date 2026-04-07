package com.example.celcoinmock.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simulates Celcoin POST /pix/v1/emv/decode
 * Parses a Pix EMV (BR Code) string and returns structured QR Code data.
 *
 * EMV TLV format: each field is TAG(2) + LENGTH(2, decimal) + VALUE
 * Key tags: 26/62 = merchant account info, 54 = amount, 52 = MCC,
 *           59 = merchant name, 60 = city, 62 = additional data field.
 */
@RestController
@RequestMapping("/pix/v1/emv")
public class EmvDecodeController {

    public record EmvRequest(String emv) {}

    @PostMapping("/decode")
    public ResponseEntity<Map<String, Object>> decode(@RequestBody EmvRequest req) {
        if (req.emv() == null || req.emv().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "emv is required"));
        }

        try {
            Map<String, String> tags = parseTlv(req.emv());

            // Extract Pix key from tag 26 sub-tag 01 (or tag 62 for dynamic)
            String pixKey = extractPixKey(tags);
            String merchantName = tags.getOrDefault("59", "Destinatário");
            String merchantCity = tags.getOrDefault("60", "");
            String txId = extractTransactionId(tags); // tag 62 sub-tag 05
            String amountStr = tags.get("54");
            BigDecimal amount = amountStr != null && !amountStr.isBlank()
                ? new BigDecimal(amountStr) : BigDecimal.ZERO;

            // Determine type: tag 01 value "12" = static, "11" = dynamic
            String payloadFormat = tags.getOrDefault("01", "12");
            boolean isDynamic = "11".equals(payloadFormat) || pixKey != null && pixKey.contains("/");
            String type = isDynamic ? "DYNAMIC_QRCODE" : "STATIC_QRCODE";
            String initiationType = isDynamic ? "DYNAMIC_QRCODE" : "STATIC_QRCODE";

            Map<String, Object> body = new HashMap<>();
            body.put("type", type);
            body.put("initiationType", initiationType);
            body.put("key", pixKey != null ? pixKey : "");
            body.put("merchantName", merchantName);
            body.put("merchantCity", merchantCity);
            body.put("transactionIdentification", txId != null ? txId : UUID.randomUUID().toString().replace("-", "").substring(0, 25));
            body.put("amount", Map.of(
                "original", amount,
                "final", amount,
                "canModifyFinalAmount", amount.compareTo(BigDecimal.ZERO) == 0
            ));

            return ResponseEntity.ok(Map.of(
                "version", "1.0.0",
                "status", 200,
                "body", body
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "version", "1.0.0",
                "status", 400,
                "error", Map.of("message", "EMV inválido: " + e.getMessage())
            ));
        }
    }

    /** Parses top-level TLV tags from EMV string (excludes CRC tag 63). */
    private Map<String, String> parseTlv(String emv) {
        Map<String, String> tags = new HashMap<>();
        int i = 0;
        while (i + 4 <= emv.length()) {
            String tag = emv.substring(i, i + 2);
            int len;
            try {
                len = Integer.parseInt(emv.substring(i + 2, i + 4));
            } catch (NumberFormatException e) { break; }
            if (i + 4 + len > emv.length()) break;
            String value = emv.substring(i + 4, i + 4 + len);
            tags.put(tag, value);
            i += 4 + len;
        }
        return tags;
    }

    /** Extracts Pix key from tag 26 (static) or tag 26 URL (dynamic). */
    private String extractPixKey(Map<String, String> tags) {
        // Tag 26 contains merchant account info sub-TLV
        String tag26 = tags.get("26");
        if (tag26 != null) {
            Map<String, String> sub = parseSubTlv(tag26);
            String key = sub.get("01"); // sub-tag 01 = Pix key for static
            if (key != null && !key.isBlank() && !key.startsWith("br.gov.bcb.pix")) return key;
            // For dynamic: sub-tag 25 or sub-tag 26 contains URL
            String url = sub.get("25");
            if (url == null) url = sub.get("26");
            if (url != null) return url; // return URL for dynamic
        }
        return null;
    }

    private String extractTransactionId(Map<String, String> tags) {
        String tag62 = tags.get("62");
        if (tag62 == null) return null;
        Map<String, String> sub = parseSubTlv(tag62);
        return sub.get("05"); // sub-tag 05 = transaction id
    }

    private Map<String, String> parseSubTlv(String data) {
        Map<String, String> sub = new HashMap<>();
        int i = 0;
        while (i + 4 <= data.length()) {
            String tag = data.substring(i, i + 2);
            int len;
            try { len = Integer.parseInt(data.substring(i + 2, i + 4)); }
            catch (NumberFormatException e) { break; }
            if (i + 4 + len > data.length()) break;
            sub.put(tag, data.substring(i + 4, i + 4 + len));
            i += 4 + len;
        }
        return sub;
    }
}
