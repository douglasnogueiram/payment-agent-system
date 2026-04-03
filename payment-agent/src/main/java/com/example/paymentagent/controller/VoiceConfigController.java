package com.example.paymentagent.controller;

import com.example.paymentagent.entity.VoiceConfig;
import com.example.paymentagent.service.VoiceConfigService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/voice-config")
public class VoiceConfigController {

    private final VoiceConfigService service;

    public VoiceConfigController(VoiceConfigService service) {
        this.service = service;
    }

    @GetMapping
    public VoiceConfig get() { return service.get(); }

    @PutMapping
    public VoiceConfig save(@RequestBody VoiceConfig config) { return service.save(config); }
}
