package com.yoyuzh.platform.storage.api;

public record StoragePolicyDescriptor(
        Long id,
        String name,
        StoragePolicyType type,
        String bucketName,
        String endpoint,
        String region,
        boolean privateBucket,
        String prefix,
        StoragePolicyCredentialMode credentialMode,
        boolean enabled,
        long maxSizeBytes
) {
}
