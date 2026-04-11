package com.reclaim.portal.catalog.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "item_fingerprints")
public class ItemFingerprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "fingerprint_hash")
    private String fingerprintHash;

    @Column(name = "normalized_attributes")
    private String normalizedAttributes;

    @Column(name = "duplicate_status")
    private String duplicateStatus;

    private boolean reviewed;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ItemFingerprint() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getFingerprintHash() {
        return fingerprintHash;
    }

    public void setFingerprintHash(String fingerprintHash) {
        this.fingerprintHash = fingerprintHash;
    }

    public String getNormalizedAttributes() {
        return normalizedAttributes;
    }

    public void setNormalizedAttributes(String normalizedAttributes) {
        this.normalizedAttributes = normalizedAttributes;
    }

    public String getDuplicateStatus() {
        return duplicateStatus;
    }

    public void setDuplicateStatus(String duplicateStatus) {
        this.duplicateStatus = duplicateStatus;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    public void setReviewed(boolean reviewed) {
        this.reviewed = reviewed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
