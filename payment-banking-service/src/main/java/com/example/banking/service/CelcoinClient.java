package com.example.banking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP client for Celcoin (mock or real sandbox).
 * Handles OAuth2 client_credentials token lifecycle automatically.
 * To switch from mock to real Celcoin, change celcoin.base-url in application.properties.
 */
@Component
public class CelcoinClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final AtomicReference<String> tokenCache = new AtomicReference<>();

    public CelcoinClient(
            @Value("${celcoin.base-url}") String baseUrl,
            @Value("${celcoin.client-id}") String clientId,
            @Value("${celcoin.client-secret}") String clientSecret
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** POST a JSON body, automatically adding Bearer token. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String uri, Object body) {
        return restClient.post()
                .uri(uri)
                .header("Authorization", "Bearer " + getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    /** GET with query params, automatically adding Bearer token. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String uriWithParams) {
        return restClient.get()
                .uri(uriWithParams)
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .body(Map.class);
    }

    // ── token lifecycle ───────────────────────────────────────────────────────

    private String getToken() {
        String cached = tokenCache.get();
        if (cached != null) return cached;
        return refreshToken();
    }

    @SuppressWarnings("unchecked")
    private synchronized String refreshToken() {
        // Double-check after acquiring lock
        String cached = tokenCache.get();
        if (cached != null) return cached;

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> response = restClient.post()
                .uri("/v5/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        String token = (String) response.get("access_token");
        tokenCache.set(token);
        return token;
    }

    /** Call when a 401 is received to force token refresh on next request. */
    public void invalidateToken() {
        tokenCache.set(null);
    }
}
