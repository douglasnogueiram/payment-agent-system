package com.example.paymentagent.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stt")
public class TranscribeController {

    private final RestClient openAiClient;

    public TranscribeController(@Value("${spring.ai.openai.api-key}") String apiKey) {
        this.openAiClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> transcribe(@RequestParam("audio") MultipartFile audio)
            throws Exception {

        String base64Audio = Base64.getEncoder().encodeToString(audio.getBytes());

        String contentType = audio.getContentType() != null ? audio.getContentType() : "";
        String format = "webm";
        if (contentType.contains("mp4") || contentType.contains("m4a")) format = "mp4";
        else if (contentType.contains("ogg"))  format = "ogg";
        else if (contentType.contains("wav"))  format = "wav";
        else if (contentType.contains("mp3") || contentType.contains("mpeg")) format = "mp3";

        Map<String, Object> body = Map.of(
            "model", "gpt-4o-audio-preview",
            "modalities", List.of("text"),
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "input_audio",
                           "input_audio", Map.of("data", base64Audio, "format", format)),
                    Map.of("type", "text",
                           "text", "Transcreva exatamente o que foi dito. Responda apenas com o texto transcrito.")
                )
            ))
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = openAiClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        String text = "";
        if (resp != null) {
            var choices = (List<?>) resp.get("choices");
            if (choices != null && !choices.isEmpty()) {
                var message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
                if (message != null) text = (String) message.get("content");
            }
        }

        return ResponseEntity.ok(Map.of("text", text != null ? text.trim() : ""));
    }
}
