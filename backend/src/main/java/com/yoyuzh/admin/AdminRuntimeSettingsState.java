package com.yoyuzh.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_admin_runtime_settings_state")
public class AdminRuntimeSettingsState {

    @Id
    private Long id;

    @Column(name = "site_supported", nullable = false)
    private boolean siteSupported;

    @Column(name = "registration_invite_code_required", nullable = false)
    private boolean registrationInviteCodeRequired;

    @Column(name = "registration_management_roles", nullable = false, length = 512)
    private String registrationManagementRoles;

    @Column(name = "user_session_access_expiration_seconds", nullable = false)
    private long userSessionAccessExpirationSeconds;

    @Column(name = "user_session_refresh_expiration_seconds", nullable = false)
    private long userSessionRefreshExpirationSeconds;

    @Column(name = "user_session_token_blacklist_enabled", nullable = false)
    private boolean userSessionTokenBlacklistEnabled;

    @Column(name = "user_session_token_blacklist_ttl_buffer_seconds", nullable = false)
    private long userSessionTokenBlacklistTtlBufferSeconds;

    @Column(name = "media_metadata_extraction_enabled", nullable = false)
    private boolean mediaMetadataExtractionEnabled;

    @Column(name = "media_thumbnail_generation_enabled", nullable = false)
    private boolean mediaThumbnailGenerationEnabled;

    @Column(name = "media_video_poster_enabled", nullable = false)
    private boolean mediaVideoPosterEnabled;

    @Column(name = "queue_backend", nullable = false, length = 64)
    private String queueBackend;

    @Column(name = "queue_media_metadata_fixed_delay_ms", nullable = false)
    private long queueMediaMetadataFixedDelayMs;

    @Column(name = "queue_media_metadata_initial_delay_ms", nullable = false)
    private long queueMediaMetadataInitialDelayMs;

    @Column(name = "appearance_supported", nullable = false)
    private boolean appearanceSupported;

    @Column(name = "server_storage_provider", nullable = false, length = 64)
    private String serverStorageProvider;

    @Column(name = "server_redis_enabled", nullable = false)
    private boolean serverRedisEnabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isSiteSupported() {
        return siteSupported;
    }

    public void setSiteSupported(boolean siteSupported) {
        this.siteSupported = siteSupported;
    }

    public boolean isRegistrationInviteCodeRequired() {
        return registrationInviteCodeRequired;
    }

    public void setRegistrationInviteCodeRequired(boolean registrationInviteCodeRequired) {
        this.registrationInviteCodeRequired = registrationInviteCodeRequired;
    }

    public String getRegistrationManagementRoles() {
        return registrationManagementRoles;
    }

    public void setRegistrationManagementRoles(String registrationManagementRoles) {
        this.registrationManagementRoles = registrationManagementRoles;
    }

    public long getUserSessionAccessExpirationSeconds() {
        return userSessionAccessExpirationSeconds;
    }

    public void setUserSessionAccessExpirationSeconds(long userSessionAccessExpirationSeconds) {
        this.userSessionAccessExpirationSeconds = userSessionAccessExpirationSeconds;
    }

    public long getUserSessionRefreshExpirationSeconds() {
        return userSessionRefreshExpirationSeconds;
    }

    public void setUserSessionRefreshExpirationSeconds(long userSessionRefreshExpirationSeconds) {
        this.userSessionRefreshExpirationSeconds = userSessionRefreshExpirationSeconds;
    }

    public boolean isUserSessionTokenBlacklistEnabled() {
        return userSessionTokenBlacklistEnabled;
    }

    public void setUserSessionTokenBlacklistEnabled(boolean userSessionTokenBlacklistEnabled) {
        this.userSessionTokenBlacklistEnabled = userSessionTokenBlacklistEnabled;
    }

    public long getUserSessionTokenBlacklistTtlBufferSeconds() {
        return userSessionTokenBlacklistTtlBufferSeconds;
    }

    public void setUserSessionTokenBlacklistTtlBufferSeconds(long userSessionTokenBlacklistTtlBufferSeconds) {
        this.userSessionTokenBlacklistTtlBufferSeconds = userSessionTokenBlacklistTtlBufferSeconds;
    }

    public boolean isMediaMetadataExtractionEnabled() {
        return mediaMetadataExtractionEnabled;
    }

    public void setMediaMetadataExtractionEnabled(boolean mediaMetadataExtractionEnabled) {
        this.mediaMetadataExtractionEnabled = mediaMetadataExtractionEnabled;
    }

    public boolean isMediaThumbnailGenerationEnabled() {
        return mediaThumbnailGenerationEnabled;
    }

    public void setMediaThumbnailGenerationEnabled(boolean mediaThumbnailGenerationEnabled) {
        this.mediaThumbnailGenerationEnabled = mediaThumbnailGenerationEnabled;
    }

    public boolean isMediaVideoPosterEnabled() {
        return mediaVideoPosterEnabled;
    }

    public void setMediaVideoPosterEnabled(boolean mediaVideoPosterEnabled) {
        this.mediaVideoPosterEnabled = mediaVideoPosterEnabled;
    }

    public String getQueueBackend() {
        return queueBackend;
    }

    public void setQueueBackend(String queueBackend) {
        this.queueBackend = queueBackend;
    }

    public long getQueueMediaMetadataFixedDelayMs() {
        return queueMediaMetadataFixedDelayMs;
    }

    public void setQueueMediaMetadataFixedDelayMs(long queueMediaMetadataFixedDelayMs) {
        this.queueMediaMetadataFixedDelayMs = queueMediaMetadataFixedDelayMs;
    }

    public long getQueueMediaMetadataInitialDelayMs() {
        return queueMediaMetadataInitialDelayMs;
    }

    public void setQueueMediaMetadataInitialDelayMs(long queueMediaMetadataInitialDelayMs) {
        this.queueMediaMetadataInitialDelayMs = queueMediaMetadataInitialDelayMs;
    }

    public boolean isAppearanceSupported() {
        return appearanceSupported;
    }

    public void setAppearanceSupported(boolean appearanceSupported) {
        this.appearanceSupported = appearanceSupported;
    }

    public String getServerStorageProvider() {
        return serverStorageProvider;
    }

    public void setServerStorageProvider(String serverStorageProvider) {
        this.serverStorageProvider = serverStorageProvider;
    }

    public boolean isServerRedisEnabled() {
        return serverRedisEnabled;
    }

    public void setServerRedisEnabled(boolean serverRedisEnabled) {
        this.serverRedisEnabled = serverRedisEnabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
