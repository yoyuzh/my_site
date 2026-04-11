package com.yoyuzh.admin;

import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyService;

final class AdminStoragePolicyResponses {

    private AdminStoragePolicyResponses() {
    }

    static AdminStoragePolicyResponse from(StoragePolicyService storagePolicyService, StoragePolicy policy) {
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
                storagePolicyService.readCapabilities(policy),
                policy.isEnabled(),
                policy.isDefaultPolicy(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
