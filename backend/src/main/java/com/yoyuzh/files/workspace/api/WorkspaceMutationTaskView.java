package com.yoyuzh.files.workspace.api;

import java.time.LocalDateTime;

public record WorkspaceMutationTaskView(
        Long id,
        String type,
        String status,
        Long userId,
        String publicStateJson,
        String correlationId,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finishedAt
) {
}
