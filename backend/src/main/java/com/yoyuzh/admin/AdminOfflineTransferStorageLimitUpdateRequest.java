package com.yoyuzh.admin;

import jakarta.validation.constraints.Positive;

public record AdminOfflineTransferStorageLimitUpdateRequest(
        @Positive(message = "离线快传存储上限必须大于 0")
        long offlineTransferStorageLimitBytes
) {
}
