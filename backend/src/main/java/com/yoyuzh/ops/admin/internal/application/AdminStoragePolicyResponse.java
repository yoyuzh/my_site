package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyCredentialMode;
import com.yoyuzh.files.policy.StoragePolicyType;

import java.time.LocalDateTime;

public record AdminStoragePolicyResponse(
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
