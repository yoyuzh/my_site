package com.yoyuzh.files.sharing.internal.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_file_share_link", indexes = {
        @Index(name = "uk_file_share_token", columnList = "token", unique = true),
        @Index(name = "idx_file_share_created_at", columnList = "created_at"),
        @Index(name = "idx_file_share_cancelled_at", columnList = "cancelled_at")
})
public class FileShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(nullable = false, length = 96, unique = true)
    private String token;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @Column(name = "download_count")
    private Long downloadCount;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "allow_import")
    private Boolean allowImport;

    @Column(name = "allow_download")
    private Boolean allowDownload;

    @Column(name = "expire_after_consume")
    private Boolean expireAfterConsume;

    @Column(name = "share_name", length = 255)
    private String shareName;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (downloadCount == null) {
            downloadCount = 0L;
        }
        if (viewCount == null) {
            viewCount = 0L;
        }
        if (allowImport == null) {
            allowImport = true;
        }
        if (allowDownload == null) {
            allowDownload = true;
        }
        if (expireAfterConsume == null) {
            expireAfterConsume = false;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Integer getMaxDownloads() {
        return maxDownloads;
    }

    public void setMaxDownloads(Integer maxDownloads) {
        this.maxDownloads = maxDownloads;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public Boolean getAllowImport() {
        return allowImport;
    }

    public void setAllowImport(Boolean allowImport) {
        this.allowImport = allowImport;
    }

    public Boolean getAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(Boolean allowDownload) {
        this.allowDownload = allowDownload;
    }

    public Boolean getExpireAfterConsume() {
        return expireAfterConsume;
    }

    public void setExpireAfterConsume(Boolean expireAfterConsume) {
        this.expireAfterConsume = expireAfterConsume;
    }

    public String getShareName() {
        return shareName;
    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public boolean isAllowImportEnabled() {
        return allowImport == null || allowImport;
    }

    public boolean isAllowDownloadEnabled() {
        return allowDownload == null || allowDownload;
    }

    public void recordVisit() {
        viewCount = getViewCountOrZero() + 1;
    }

    public void recordDownload() {
        downloadCount = getDownloadCountOrZero() + 1;
    }

    public void markConsumed(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public void cancel(LocalDateTime cancelledAt) {
        if (this.cancelledAt == null) {
            this.cancelledAt = cancelledAt;
        }
    }

    public boolean isDownloadLimitReached() {
        return maxDownloads != null && getDownloadCountOrZero() >= maxDownloads;
    }

    public long getDownloadCountOrZero() {
        return downloadCount == null ? 0L : downloadCount;
    }

    public long getViewCountOrZero() {
        return viewCount == null ? 0L : viewCount;
    }

    public boolean isExpireAfterConsumeEnabled() {
        return expireAfterConsume != null && expireAfterConsume;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public String getShareNameOrDefault() {
        if (shareName != null && !shareName.isBlank()) {
            return shareName;
        }
        return null;
    }
}
