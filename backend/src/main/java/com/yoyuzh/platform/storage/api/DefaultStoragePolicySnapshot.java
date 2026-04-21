package com.yoyuzh.platform.storage.api;

public record DefaultStoragePolicySnapshot(
        Long policyId,
        long policyMaxSizeBytes,
        StoragePolicyCapabilities capabilities
) {
}
