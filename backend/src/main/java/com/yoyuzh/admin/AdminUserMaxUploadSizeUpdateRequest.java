package com.yoyuzh.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminUserMaxUploadSizeUpdateRequest(
        @NotNull
        @Positive
        Long maxUploadSizeBytes
) {
}
