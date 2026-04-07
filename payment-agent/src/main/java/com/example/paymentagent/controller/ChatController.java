package com.example.paymentagent.controller;

import com.example.paymentagent.agent.PaymentAssistant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final PaymentAssistant assistant;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatController(PaymentAssistant assistant) {
        this.assistant = assistant;
    }

    public record ChatRequest(String chatId, String conversationId, String message,
                              String imageBase64, String imageMimeType) {
        public String resolvedConversationId() {
            return chatId != null ? chatId : (conversationId != null ? conversationId : "default");
        }
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest req, @AuthenticationPrincipal Jwt jwt) {
        String keycloakUserId = jwt.getSubject();
        String givenName  = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");
        String name  = (givenName != null && familyName != null)
            ? (givenName + " " + familyName).trim()
            : jwt.getClaimAsString("name");
        String email = jwt.getClaimAsString("email");

        return assistant.chat(req.resolvedConversationId(), req.message(),
                              req.imageBase64(), req.imageMimeType(),
                              keycloakUserId, name, email)
                .map(token -> toSseEvent("token", token))
                .concatWith(Flux.just(toSseEvent("done", "")))
                .onErrorResume(e -> Flux.just(toSseEvent("error", e.getMessage())));
    }

    @PostMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatMessage(@RequestBody ChatRequest req, @AuthenticationPrincipal Jwt jwt) {
        return chat(req, jwt);
    }

    private String toSseEvent(String type, String content) {
        try {
            return mapper.writeValueAsString(Map.of("type", type, "content", content != null ? content : ""));
        } catch (JsonProcessingException e) {
            return "{\"type\":\"" + type + "\",\"content\":\"\"}";
        }
    }
}
