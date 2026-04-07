package com.example.paymentagent.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class AccountController {

    private final RestClient bankingClient;

    public AccountController(@Value("${banking.service.url}") String bankingUrl) {
        this.bankingClient = RestClient.builder().baseUrl(bankingUrl).build();
    }

    /** Returns the account linked to the authenticated user, or 404 if none. */
    @GetMapping("/account")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> getMyAccount(@AuthenticationPrincipal Jwt jwt) {
        // Try by keycloakUserId (sub) first; fall back to email since sub may be absent in some tokens.
        Map<String, Object> account = resolveAccount(jwt.getSubject(), jwt.getClaimAsString("email"));
        return account != null ? ResponseEntity.ok(account) : ResponseEntity.notFound().build();
    }

    private Map<String, Object> resolveAccount(String keycloakUserId, String email) {
        if (keycloakUserId != null && !keycloakUserId.isBlank()) {
            Map<String, Object> a = fetchAccount("/api/accounts/by-user/" + keycloakUserId);
            if (a != null) return a;
        }
        if (email != null && !email.isBlank()) {
            return fetchAccount("/api/accounts/by-email/" + email);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchAccount(String uri) {
        try {
            return bankingClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
                    .body(Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
