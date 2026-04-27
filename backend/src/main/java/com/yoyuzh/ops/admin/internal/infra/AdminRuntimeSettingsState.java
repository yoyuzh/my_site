package com.yoyuzh.ops.admin.internal.infra;

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
    private Boolean siteSupported;

    @Column(name = "registration_invite_code_required", nullable = false)
    private Boolean registrationInviteCodeRequired;

    @Column(name = "registration_management_roles", nullable = false, length = 512)
    private String registrationManagementRoles;

    @Column(name = "user_session_access_expiration_seconds", nullable = false)
    private Long userSessionAccessExpirationSeconds;

    @Column(name = "user_session_refresh_expiration_seconds", nullable = false)
    private Long userSessionRefreshExpirationSeconds;

    @Column(name = "user_session_token_blacklist_enabled", nullable = false)
    private Boolean userSessionTokenBlacklistEnabled;

    @Column(name = "user_session_token_blacklist_ttl_buffer_seconds", nullable = false)
    private Long userSessionTokenBlacklistTtlBufferSeconds;

    @Column(name = "media_metadata_extraction_enabled", nullable = false)
    private Boolean mediaMetadataExtractionEnabled;

    @Column(name = "media_thumbnail_generation_enabled", nullable = false)
    private Boolean mediaThumbnailGenerationEnabled;

    @Column(name = "media_video_poster_enabled", nullable = false)
    private Boolean mediaVideoPosterEnabled;

    @Column(name = "queue_backend", nullable = false, length = 64)
    private String queueBackend;

    @Column(name = "queue_media_metadata_fixed_delay_ms", nullable = false)
    private Long queueMediaMetadataFixedDelayMs;

    @Column(name = "queue_media_metadata_initial_delay_ms", nullable = false)
    private Long queueMediaMetadataInitialDelayMs;

    @Column(name = "appearance_supported", nullable = false)
    private Boolean appearanceSupported;

    @Column(name = "server_storage_provider", nullable = false, length = 64)
    private String serverStorageProvider;

    @Column(name = "server_redis_enabled", nullable = false)
    private Boolean serverRedisEnabled;

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

    public Boolean isSiteSupported() {
        return siteSupported;
    }

    public void setSiteSupported(Boolean siteSupported) {
        this.siteSupported = siteSupported;
    }

    public Boolean isRegistrationInviteCodeRequired() {
        return registrationInviteCodeRequired;
    }

    public void setRegistrationInviteCodeRequired(Boolean registrationInviteCodeRequired) {
        this.registrationInviteCodeRequired = registrationInviteCodeRequired;
    }

    public String getRegistrationManagementRoles() {
        return registrationManagementRoles;
    }

    public void setRegistrationManagementRoles(String registrationManagementRoles) {
        this.registrationManagementRoles = registrationManagementRoles;
    }

    public Long getUserSessionAccessExpirationSeconds() {
        return userSessionAccessExpirationSeconds;
    }

    public void setUserSessionAccessExpirationSeconds(Long userSessionAccessExpirationSeconds) {
        this.userSessionAccessExpirationSeconds = userSessionAccessExpirationSeconds;
    }

    public Long getUserSessionRefreshExpirationSeconds() {
        return userSessionRefreshExpirationSeconds;
    }

    public void setUserSessionRefreshExpirationSeconds(Long userSessionRefreshExpirationSeconds) {
        this.userSessionRefreshExpirationSeconds = userSessionRefreshExpirationSeconds;
    }

    public Boolean isUserSessionTokenBlacklistEnabled() {
        return userSessionTokenBlacklistEnabled;
    }

    public void setUserSessionTokenBlacklistEnabled(Boolean userSessionTokenBlacklistEnabled) {
        this.userSessionTokenBlacklistEnabled = userSessionTokenBlacklistEnabled;
    }

    public Long getUserSessionTokenBlacklistTtlBufferSeconds() {
        return userSessionTokenBlacklistTtlBufferSeconds;
    }

    public void setUserSessionTokenBlacklistTtlBufferSeconds(Long userSessionTokenBlacklistTtlBufferSeconds) {
        this.userSessionTokenBlacklistTtlBufferSeconds = userSessionTokenBlacklistTtlBufferSeconds;
    }

    public Boolean isMediaMetadataExtractionEnabled() {
        return mediaMetadataExtractionEnabled;
    }

    public void setMediaMetadataExtractionEnabled(Boolean mediaMetadataExtractionEnabled) {
        this.mediaMetadataExtractionEnabled = mediaMetadataExtractionEnabled;
    }

    public Boolean isMediaThumbnailGenerationEnabled() {
        return mediaThumbnailGenerationEnabled;
    }

    public void setMediaThumbnailGenerationEnabled(Boolean mediaThumbnailGenerationEnabled) {
        this.mediaThumbnailGenerationEnabled = mediaThumbnailGenerationEnabled;
    }

    public Boolean isMediaVideoPosterEnabled() {
        return mediaVideoPosterEnabled;
    }

    public void setMediaVideoPosterEnabled(Boolean mediaVideoPosterEnabled) {
        this.mediaVideoPosterEnabled = mediaVideoPosterEnabled;
    }

    public String getQueueBackend() {
        return queueBackend;
    }

    public void setQueueBackend(String queueBackend) {
        this.queueBackend = queueBackend;
    }

    public Long getQueueMediaMetadataFixedDelayMs() {
        return queueMediaMetadataFixedDelayMs;
    }

    public void setQueueMediaMetadataFixedDelayMs(Long queueMediaMetadataFixedDelayMs) {
        this.queueMediaMetadataFixedDelayMs = queueMediaMetadataFixedDelayMs;
    }

    public Long getQueueMediaMetadataInitialDelayMs() {
        return queueMediaMetadataInitialDelayMs;
    }

    public void setQueueMediaMetadataInitialDelayMs(Long queueMediaMetadataInitialDelayMs) {
        this.queueMediaMetadataInitialDelayMs = queueMediaMetadataInitialDelayMs;
    }

    public Boolean isAppearanceSupported() {
        return appearanceSupported;
    }

    public void setAppearanceSupported(Boolean appearanceSupported) {
        this.appearanceSupported = appearanceSupported;
    }

    public String getServerStorageProvider() {
        return serverStorageProvider;
    }

    public void setServerStorageProvider(String serverStorageProvider) {
        this.serverStorageProvider = serverStorageProvider;
    }

    public Boolean isServerRedisEnabled() {
        return serverRedisEnabled;
    }

    public void setServerRedisEnabled(Boolean serverRedisEnabled) {
        this.serverRedisEnabled = serverRedisEnabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
