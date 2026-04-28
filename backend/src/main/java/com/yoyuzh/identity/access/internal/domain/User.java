package com.yoyuzh.identity.access.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_user", indexes = {
        @Index(name = "uk_user_username", columnList = "username", unique = true),
        @Index(name = "uk_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_created_at", columnList = "created_at")
})
public class User {
    public static final long DEFAULT_STORAGE_QUOTA_BYTES = 50L * 1024 * 1024 * 1024;
    public static final long DEFAULT_MAX_UPLOAD_SIZE_BYTES = 500L * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String username;

    @Column(nullable = false, length = 128, unique = true)
    private String email;

    @Column(name = "phone_number", length = 32, unique = true)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(length = 280)
    private String bio;

    @Column(name = "preferred_language", nullable = false, length = 16)
    private String preferredLanguage;

    @Column(name = "preferred_theme", nullable = false, length = 16)
    private String preferredTheme;

    @Column(name = "disable_view_sync", nullable = false)
    private boolean disableViewSync;

    @Column(name = "default_open_with_by_ext", columnDefinition = "TEXT")
    private String defaultOpenWithByExtJson;

    @Column(name = "avatar_storage_name", length = 255)
    private String avatarStorageName;

    @Column(name = "avatar_content_type", length = 128)
    private String avatarContentType;

    @Column(name = "avatar_updated_at")
    private LocalDateTime avatarUpdatedAt;

    @Column(name = "active_session_id", length = 64)
    private String activeSessionId;

    @Column(name = "desktop_active_session_id", length = 64)
    private String desktopActiveSessionId;

    @Column(name = "mobile_active_session_id", length = 64)
    private String mobileActiveSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(nullable = false)
    private boolean banned;

    @Column(name = "storage_quota_bytes")
    private Long storageQuotaBytes;

    @Column(name = "max_upload_size_bytes")
    private Long maxUploadSizeBytes;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (role == null) {
            role = UserRole.USER;
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = username;
        }
        if (preferredLanguage == null || preferredLanguage.isBlank()) {
            preferredLanguage = "zh-CN";
        }
        if (preferredTheme == null || preferredTheme.isBlank()) {
            preferredTheme = "system";
        }
        if (storageQuotaBytes == null || storageQuotaBytes <= 0) {
            storageQuotaBytes = DEFAULT_STORAGE_QUOTA_BYTES;
        }
        if (maxUploadSizeBytes == null || maxUploadSizeBytes <= 0) {
            maxUploadSizeBytes = DEFAULT_MAX_UPLOAD_SIZE_BYTES;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public String getPreferredTheme() {
        return preferredTheme;
    }

    public void setPreferredTheme(String preferredTheme) {
        this.preferredTheme = preferredTheme;
    }

    public boolean isDisableViewSync() {
        return disableViewSync;
    }

    public void setDisableViewSync(boolean disableViewSync) {
        this.disableViewSync = disableViewSync;
    }

    public String getDefaultOpenWithByExtJson() {
        return defaultOpenWithByExtJson;
    }

    public void setDefaultOpenWithByExtJson(String defaultOpenWithByExtJson) {
        this.defaultOpenWithByExtJson = defaultOpenWithByExtJson;
    }

    public String getAvatarStorageName() {
        return avatarStorageName;
    }

    public void setAvatarStorageName(String avatarStorageName) {
        this.avatarStorageName = avatarStorageName;
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public void setAvatarContentType(String avatarContentType) {
        this.avatarContentType = avatarContentType;
    }

    public LocalDateTime getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public void setAvatarUpdatedAt(LocalDateTime avatarUpdatedAt) {
        this.avatarUpdatedAt = avatarUpdatedAt;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public void setActiveSessionId(String activeSessionId) {
        this.activeSessionId = activeSessionId;
    }

    public String getDesktopActiveSessionId() {
        return desktopActiveSessionId;
    }

    public void setDesktopActiveSessionId(String desktopActiveSessionId) {
        this.desktopActiveSessionId = desktopActiveSessionId;
    }

    public String getMobileActiveSessionId() {
        return mobileActiveSessionId;
    }

    public void setMobileActiveSessionId(String mobileActiveSessionId) {
        this.mobileActiveSessionId = mobileActiveSessionId;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isBanned() {
        return banned;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }

    public long getStorageQuotaBytes() {
        if (storageQuotaBytes == null || storageQuotaBytes <= 0) {
            return DEFAULT_STORAGE_QUOTA_BYTES;
        }
        return storageQuotaBytes;
    }

    public void setStorageQuotaBytes(Long storageQuotaBytes) {
        this.storageQuotaBytes = storageQuotaBytes;
    }

    public long getMaxUploadSizeBytes() {
        if (maxUploadSizeBytes == null || maxUploadSizeBytes <= 0) {
            return DEFAULT_MAX_UPLOAD_SIZE_BYTES;
        }
        return maxUploadSizeBytes;
    }

    public void setMaxUploadSizeBytes(Long maxUploadSizeBytes) {
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }
}
