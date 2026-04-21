package com.yoyuzh.platform.job.internal.domain;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_background_task", indexes = {
        @Index(name = "idx_background_task_user_created_at", columnList = "user_id,created_at"),
        @Index(name = "idx_background_task_status_created_at", columnList = "status,created_at"),
        @Index(name = "idx_background_task_status_lease_expires_at", columnList = "status,lease_expires_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_background_task_correlation_id", columnNames = "correlation_id")
})
public class BackgroundTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private BackgroundTaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BackgroundTaskStatus status;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "public_state_json", nullable = false, length = 8192)
    private String publicStateJson;

    @Column(name = "private_state_json", nullable = false, length = 8192)
    private String privateStateJson;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "heartbeat_at")
    private LocalDateTime heartbeatAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = BackgroundTaskStatus.QUEUED;
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (maxAttempts == null) {
            maxAttempts = 1;
        }
        if (publicStateJson == null) {
            publicStateJson = "{}";
        }
        if (privateStateJson == null) {
            privateStateJson = "{}";
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

    public BackgroundTaskType getType() {
        return type;
    }

    public void setType(BackgroundTaskType type) {
        this.type = type;
    }

    public BackgroundTaskStatus getStatus() {
        return status;
    }

    public void setStatus(BackgroundTaskStatus status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPublicStateJson() {
        return publicStateJson;
    }

    public void setPublicStateJson(String publicStateJson) {
        this.publicStateJson = publicStateJson;
    }

    public String getPrivateStateJson() {
        return privateStateJson;
    }

    public void setPrivateStateJson(String privateStateJson) {
        this.privateStateJson = privateStateJson;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public void setLeaseOwner(String leaseOwner) {
        this.leaseOwner = leaseOwner;
    }

    public LocalDateTime getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(LocalDateTime leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public LocalDateTime getHeartbeatAt() {
        return heartbeatAt;
    }

    public void setHeartbeatAt(LocalDateTime heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
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

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public boolean isTerminal() {
        return status == BackgroundTaskStatus.FAILED
                || status == BackgroundTaskStatus.CANCELLED
                || status == BackgroundTaskStatus.COMPLETED;
    }
}
