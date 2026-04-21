package com.yoyuzh.platform.job.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBackgroundTaskRequest(
        @NotNull Long fileId,
        @NotBlank String path,
        String correlationId
) {
}
