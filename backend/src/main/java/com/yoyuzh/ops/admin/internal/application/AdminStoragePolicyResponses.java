package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;

final class AdminStoragePolicyResponses {

    private AdminStoragePolicyResponses() {
    }

    static AdminStoragePolicyResponse from(StoragePolicy policy, StoragePolicyCapabilities capabilities) {
        return new AdminStoragePolicyResponse(
                policy.getId(),
                policy.getName(),
                policy.getType(),
                policy.getBucketName(),
                policy.getEndpoint(),
                policy.getRegion(),
                policy.isPrivateBucket(),
                policy.getPrefix(),
                policy.getCredentialMode(),
                policy.getMaxSizeBytes(),
                capabilities,
                policy.isEnabled(),
                policy.isDefaultPolicy(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
