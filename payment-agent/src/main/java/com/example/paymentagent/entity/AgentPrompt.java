package com.example.paymentagent.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_prompts")
public class AgentPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private String description;

    private boolean active;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
