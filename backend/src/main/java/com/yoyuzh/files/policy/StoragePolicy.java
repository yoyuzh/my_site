package com.yoyuzh.files.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_storage_policy", indexes = {
        @Index(name = "idx_storage_policy_enabled", columnList = "enabled"),
        @Index(name = "idx_storage_policy_default", columnList = "default_policy")
})
public class StoragePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StoragePolicyType type;

    @Column(name = "bucket_name", length = 255)
    private String bucketName;

    @Column(length = 512)
    private String endpoint;

    @Column(length = 64)
    private String region;

    @Column(name = "private_bucket", nullable = false)
    private boolean privateBucket;

    @Column(length = 512)
    private String prefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_mode", nullable = false, length = 32)
    private StoragePolicyCredentialMode credentialMode;

    @Column(name = "max_size_bytes", nullable = false)
    private long maxSizeBytes;

    @Column(name = "capabilities_json", columnDefinition = "TEXT")
    private String capabilitiesJson;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "default_policy", nullable = false)
    private boolean defaultPolicy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StoragePolicyType getType() {
        return type;
    }

    public void setType(StoragePolicyType type) {
        this.type = type;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isPrivateBucket() {
        return privateBucket;
    }

    public void setPrivateBucket(boolean privateBucket) {
        this.privateBucket = privateBucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public StoragePolicyCredentialMode getCredentialMode() {
        return credentialMode;
    }

    public void setCredentialMode(StoragePolicyCredentialMode credentialMode) {
        this.credentialMode = credentialMode;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public String getCapabilitiesJson() {
        return capabilitiesJson;
    }

    public void setCapabilitiesJson(String capabilitiesJson) {
        this.capabilitiesJson = capabilitiesJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(boolean defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
