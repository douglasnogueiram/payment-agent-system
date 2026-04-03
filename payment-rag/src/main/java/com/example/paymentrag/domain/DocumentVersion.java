package com.example.paymentrag.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_document_version", indexes = {
        @Index(name = "idx_payment_version_name", columnList = "name")
})
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "sha256_hash", length = 64)
    private String sha256Hash;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private int chunks;

    @Column(nullable = false)
    private String action; // CREATED, UPDATED, DELETED

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public int getChunks() { return chunks; }
    public void setChunks(int chunks) { this.chunks = chunks; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
