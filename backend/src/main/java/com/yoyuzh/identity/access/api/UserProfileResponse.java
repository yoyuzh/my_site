package com.yoyuzh.identity.access.api;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String username,
        String displayName,
        String email,
        String phoneNumber,
        String bio,
        String preferredLanguage,
        String avatarUrl,
        IdentityRoleName role,
        LocalDateTime createdAt,
        long storageQuotaBytes,
        long maxUploadSizeBytes
) {
    private static final long DEFAULT_STORAGE_QUOTA_BYTES = 50L * 1024 * 1024 * 1024;
    private static final long DEFAULT_MAX_UPLOAD_SIZE_BYTES = 500L * 1024 * 1024;

    public UserProfileResponse(Long id, String username, String email, LocalDateTime createdAt) {
        this(
                id,
                username,
                username,
                email,
                null,
                null,
                "zh-CN",
                null,
                IdentityRoleName.USER,
                createdAt,
                DEFAULT_STORAGE_QUOTA_BYTES,
                DEFAULT_MAX_UPLOAD_SIZE_BYTES
        );
    }
}
