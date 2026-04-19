package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import java.time.LocalDateTime;

public record AdminTaskResponse(
        Long id,
        BackgroundTaskType type,
        BackgroundTaskStatus status,
        Long userId,
        String ownerUsername,
        String ownerEmail,
        String publicStateJson,
        String correlationId,
        String errorMessage,
        Integer attemptCount,
        Integer maxAttempts,
        LocalDateTime nextRunAt,
        String leaseOwner,
        LocalDateTime leaseExpiresAt,
        LocalDateTime heartbeatAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finishedAt,
        String failureCategory,
        Boolean retryScheduled,
        String workerOwner,
        AdminTaskLeaseState leaseState
) {
}
