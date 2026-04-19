package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import org.springframework.stereotype.Service;

@Service
public class RuntimeUploadConstraintPolicy implements UploadConstraintPolicy {

    @Override
    public long resolveEffectiveMaxUploadSize(long systemMaxFileSize,
                                              User user,
                                              StoragePolicy policy,
                                              StoragePolicyCapabilities capabilities) {
        long effectiveMaxUploadSize = Math.min(systemMaxFileSize, user.getMaxUploadSizeBytes());
        if (policy != null && policy.getMaxSizeBytes() > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, policy.getMaxSizeBytes());
        }
        if (capabilities != null && capabilities.maxObjectSize() > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, capabilities.maxObjectSize());
        }
        return effectiveMaxUploadSize;
    }
}
