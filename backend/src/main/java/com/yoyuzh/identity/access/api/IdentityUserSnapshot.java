package com.yoyuzh.identity.access.api;

import java.time.LocalDateTime;

public record IdentityUserSnapshot(
        Long id,
        String username,
        String displayName,
        String email,
        String phoneNumber,
        String bio,
        String preferredLanguage,
        String avatarStorageName,
        String avatarContentType,
        LocalDateTime avatarUpdatedAt,
        IdentityRoleName role,
        LocalDateTime createdAt,
        long storageQuotaBytes,
        long maxUploadSizeBytes
) {
}
