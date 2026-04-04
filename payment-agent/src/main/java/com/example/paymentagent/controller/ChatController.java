package com.example.paymentagent.controller;

import com.example.paymentagent.agent.PaymentAssistant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
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

    // Frontend sends { chatId, message } — chatId used as conversationId
    public record ChatRequest(String chatId, String conversationId, String message) {
        public String resolvedConversationId() {
            return chatId != null ? chatId : (conversationId != null ? conversationId : "default");
        }
    }

    /** POST /api/chat — SSE stream of { type, content } JSON events */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest req) {
        return assistant.chat(req.resolvedConversationId(), req.message())
                .map(token -> toSseEvent("token", token))
                .concatWith(Flux.just(toSseEvent("done", "")))
                .onErrorResume(e -> Flux.just(toSseEvent("error", e.getMessage())));
    }

    /** POST /api/chat/message — same, for frontend compatibility */
    @PostMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatMessage(@RequestBody ChatRequest req) {
        return chat(req);
    }

    private String toSseEvent(String type, String content) {
        try {
            return mapper.writeValueAsString(Map.of("type", type, "content", content != null ? content : ""));
        } catch (JsonProcessingException e) {
            return "{\"type\":\"" + type + "\",\"content\":\"\"}";
        }
    }
}
