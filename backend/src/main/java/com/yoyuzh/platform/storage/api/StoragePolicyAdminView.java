package com.yoyuzh.platform.storage.api;

import java.time.LocalDateTime;

public record StoragePolicyAdminView(
        Long id,
        String name,
        StoragePolicyType type,
        String bucketName,
        String endpoint,
        String region,
        boolean privateBucket,
        String prefix,
        StoragePolicyCredentialMode credentialMode,
        long maxSizeBytes,
        StoragePolicyCapabilities capabilities,
        boolean enabled,
        boolean defaultPolicy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
