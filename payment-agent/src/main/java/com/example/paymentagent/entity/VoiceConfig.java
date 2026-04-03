package com.example.paymentagent.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "voice_config")
public class VoiceConfig {

    @Id
    private Long id = 1L;

    private String voice = "alloy";
    private double speed = 1.0;
    private String format = "mp3";

    @Column(columnDefinition = "TEXT")
    private String instructions = "Speak clearly and professionally, as a banking assistant.";

    public Long getId() { return id; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
}
