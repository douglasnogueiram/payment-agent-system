package com.example.paymentagent.service;

import com.example.paymentagent.entity.VoiceConfig;
import com.example.paymentagent.repository.VoiceConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class VoiceConfigService {

    private final VoiceConfigRepository repository;
    private final AtomicReference<VoiceConfig> cache = new AtomicReference<>();

    public VoiceConfigService(VoiceConfigRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        VoiceConfig config = repository.findById(1L).orElseGet(() -> repository.save(new VoiceConfig()));
        cache.set(config);
    }

    public VoiceConfig get() {
        return cache.get();
    }

    public VoiceConfig save(VoiceConfig config) {
        config = repository.save(config);
        cache.set(config);
        return config;
    }
}
