package com.yoyuzh.api.v2.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBackgroundTaskRequest(
        @NotNull Long fileId,
        @NotBlank String path,
        String correlationId
) {
}
