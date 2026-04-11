package com.yoyuzh.admin;

import com.yoyuzh.files.tasks.BackgroundTaskStatus;
import com.yoyuzh.files.tasks.BackgroundTaskType;

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
