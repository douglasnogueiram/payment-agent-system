package com.example.celcoinmock.controller;

import com.example.celcoinmock.service.TokenService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    private final TokenService tokenService;

    public AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * POST /v5/token
     * grant_type=client_credentials&client_id=xxx&client_secret=yyy
     * Returns a mock Bearer token — same contract as real Celcoin.
     */
    @PostMapping(value = "/v5/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public TokenService.TokenResponse token(
            @RequestParam String grant_type,
            @RequestParam String client_id,
            @RequestParam String client_secret
    ) {
        return tokenService.issueToken(client_id, client_secret);
    }
}
