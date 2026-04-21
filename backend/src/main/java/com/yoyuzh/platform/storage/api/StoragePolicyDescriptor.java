package com.yoyuzh.platform.storage.api;

public record StoragePolicyDescriptor(
        Long id,
        String name,
        StoragePolicyType type,
        boolean enabled,
        long maxSizeBytes
) {
}
