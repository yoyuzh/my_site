package com.yoyuzh.platform.job.api;

import java.time.LocalDateTime;

public record BackgroundTaskView(
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
