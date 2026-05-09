package com.yoyuzh.ops.admin.internal.infra.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "portal_admin_config_change_log",
        indexes = {
                @Index(name = "idx_admin_config_change_key_version", columnList = "config_key,version"),
                @Index(name = "idx_admin_config_change_key_created", columnList = "config_key,created_at")
        }
)
public class AdminConfigChangeLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, length = 160)
    private String configKey;

    @Column(name = "before_value_json", nullable = false, columnDefinition = "TEXT")
    private String beforeValueJson;

    @Column(name = "after_value_json", nullable = false, columnDefinition = "TEXT")
    private String afterValueJson;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getBeforeValueJson() {
        return beforeValueJson;
    }

    public void setBeforeValueJson(String beforeValueJson) {
        this.beforeValueJson = beforeValueJson;
    }

    public String getAfterValueJson() {
        return afterValueJson;
    }

    public void setAfterValueJson(String afterValueJson) {
        this.afterValueJson = afterValueJson;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
