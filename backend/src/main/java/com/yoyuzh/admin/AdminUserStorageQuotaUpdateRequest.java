package com.yoyuzh.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminUserStorageQuotaUpdateRequest(
        @NotNull
        @Positive
        Long storageQuotaBytes
) {
}
