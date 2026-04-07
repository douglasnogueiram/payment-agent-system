package com.example.paymentagent.controller;

import com.example.paymentagent.entity.VoiceConfig;
import com.example.paymentagent.service.VoiceConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tts")
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
        // tts-1 does not support the 'instructions' field (only gpt-4o-mini-tts does)
        Map<String, Object> body = new HashMap<>();
        body.put("model", "tts-1");
        body.put("input", req.text());
        body.put("voice", cfg.getVoice());
        body.put("speed", cfg.getSpeed());
        body.put("response_format", cfg.getFormat());
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
