package com.yoyuzh.identity.access.api;

import java.time.LocalDateTime;

public record IdentityAdminUserView(
        Long id,
        String username,
        String email,
        String phoneNumber,
        LocalDateTime createdAt,
        IdentityRoleName role,
        boolean banned,
        long storageQuotaBytes,
        long maxUploadSizeBytes
) {
}
