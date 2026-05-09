package com.yoyuzh.ops.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminConfigUpdateRequest(
        @NotNull(message = "value is required")
        Object value,
        @Size(max = 255, message = "reason too long")
        String reason
) {
}
