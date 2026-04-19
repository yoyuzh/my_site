package com.yoyuzh.platform.job.api;

import java.time.LocalDateTime;

public record AdminBackgroundTaskView(
        Long id,
        BackgroundTaskType type,
        BackgroundTaskStatus status,
        Long userId,
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
        BackgroundTaskFailureCategory failureCategory,
        Boolean retryScheduled,
        String workerOwner,
        BackgroundTaskLeaseState leaseState
) {
}
