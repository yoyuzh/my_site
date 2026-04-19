package com.yoyuzh.ops.admin.api;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String phoneNumber,
        LocalDateTime createdAt,
        AdminUserRole role,
        boolean banned,
        long usedStorageBytes,
        long storageQuotaBytes,
        long maxUploadSizeBytes
) {
}
