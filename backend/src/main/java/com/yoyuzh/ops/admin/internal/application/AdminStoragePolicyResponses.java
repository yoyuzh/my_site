package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.platform.storage.api.StoragePolicyAdminView;

final class AdminStoragePolicyResponses {

    private AdminStoragePolicyResponses() {
    }

    static AdminStoragePolicyResponse from(StoragePolicyAdminView policy) {
        return new AdminStoragePolicyResponse(
                policy.id(),
                policy.name(),
                policy.type(),
                policy.bucketName(),
                policy.endpoint(),
                policy.region(),
                policy.privateBucket(),
                policy.prefix(),
                policy.credentialMode(),
                policy.maxSizeBytes(),
                policy.capabilities(),
                policy.enabled(),
                policy.defaultPolicy(),
                policy.createdAt(),
                policy.updatedAt()
        );
    }
}
