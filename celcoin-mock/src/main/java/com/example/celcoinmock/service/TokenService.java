package com.example.celcoinmock.service;

import org.springframework.stereotype.Service;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {

    // In-memory token store: token -> clientId
    private final ConcurrentHashMap<String, String> tokenStore = new ConcurrentHashMap<>();

    public record TokenResponse(String access_token, String token_type, int expires_in, String scope) {}

    public TokenResponse issueToken(String clientId, String clientSecret) {
        // Mock: any client_id/client_secret is accepted
        String token = Base64.getEncoder().encodeToString(
            (clientId + ":" + UUID.randomUUID()).getBytes()
        );
        tokenStore.put(token, clientId);
        return new TokenResponse(token, "Bearer", 3600, "banking");
    }

    public boolean isValid(String token) {
        if (token == null) return false;
        String bare = token.startsWith("Bearer ") ? token.substring(7) : token;
        return tokenStore.containsKey(bare);
    }
}
