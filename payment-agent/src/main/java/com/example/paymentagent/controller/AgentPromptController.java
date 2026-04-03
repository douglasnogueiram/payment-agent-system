package com.example.paymentagent.controller;

import com.example.paymentagent.entity.AgentPrompt;
import com.example.paymentagent.service.AgentPromptService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/agent-prompt")
public class AgentPromptController {

    private final AgentPromptService service;

    public AgentPromptController(AgentPromptService service) {
        this.service = service;
    }

    @GetMapping("/active")
    public AgentPrompt getActive() { return service.getActive(); }

    @GetMapping("/history")
    public List<AgentPrompt> getHistory() { return service.getHistory(); }

    public record SaveRequest(String content, String description) {}

    @PostMapping
    public AgentPrompt save(@RequestBody SaveRequest req) {
        return service.saveNewVersion(req.content(), req.description());
    }

    @PostMapping("/{id}/activate")
    public AgentPrompt activate(@PathVariable Long id) {
        return service.activate(id);
    }
}
