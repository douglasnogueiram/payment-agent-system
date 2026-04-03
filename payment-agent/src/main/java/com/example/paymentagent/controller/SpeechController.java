package com.example.paymentagent.controller;

import com.example.paymentagent.entity.VoiceConfig;
import com.example.paymentagent.service.VoiceConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import java.util.Map;

@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    private final VoiceConfigService voiceConfigService;
    private final RestClient openAiClient;

    public SpeechController(
            VoiceConfigService voiceConfigService,
            @Value("${spring.ai.openai.api-key}") String apiKey
    ) {
        this.voiceConfigService = voiceConfigService;
        this.openAiClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public record SpeechRequest(String text) {}

    @PostMapping(produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(@RequestBody SpeechRequest req) {
        VoiceConfig cfg = voiceConfigService.get();
        var body = Map.of(
                "model", "gpt-4o-mini-tts-2025-03-20",
                "input", req.text(),
                "voice", cfg.getVoice(),
                "speed", cfg.getSpeed(),
                "response_format", cfg.getFormat(),
                "instructions", cfg.getInstructions()
        );
        byte[] audio = openAiClient.post()
                .uri("/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(byte[].class);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(audio);
    }
}
