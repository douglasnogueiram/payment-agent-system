package com.example.paymentagent.controller;

import com.example.paymentagent.agent.PaymentAssistant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final PaymentAssistant assistant;

    public ChatController(PaymentAssistant assistant) {
        this.assistant = assistant;
    }

    public record ChatRequest(String conversationId, String message) {}

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest req) {
        return assistant.chat(req.conversationId(), req.message());
    }
}
