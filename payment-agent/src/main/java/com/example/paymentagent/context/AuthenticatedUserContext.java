package com.example.paymentagent.context;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped holder for the authenticated user's JWT claims.
 * Populated by ChatController from the incoming JWT and consumed by PaymentTools.
 * This ensures name and email always come from Keycloak — never from user input.
 */
@Component
@RequestScope
public class AuthenticatedUserContext {

    private String keycloakUserId;
    private String name;
    private String email;

    public String getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(String keycloakUserId) { this.keycloakUserId = keycloakUserId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
