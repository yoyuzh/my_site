package com.yoyuzh.api.v2.tasks;

import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import java.time.LocalDateTime;

public record BackgroundTaskResponse(
        Long id,
        BackgroundTaskType type,
        BackgroundTaskStatus status,
        Long userId,
        String publicStateJson,
        String correlationId,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finishedAt
) {
}
