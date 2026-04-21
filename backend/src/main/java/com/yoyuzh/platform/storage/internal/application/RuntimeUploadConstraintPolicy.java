package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import org.springframework.stereotype.Service;

@Service
public class RuntimeUploadConstraintPolicy implements UploadConstraintPolicy {

    @Override
    public long resolveEffectiveMaxUploadSize(long systemMaxFileSize,
                                              long userMaxUploadSizeBytes,
                                              long policyMaxSizeBytes,
                                              long maxObjectSize) {
        long effectiveMaxUploadSize = Math.min(systemMaxFileSize, userMaxUploadSizeBytes);
        if (policyMaxSizeBytes > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, policyMaxSizeBytes);
        }
        if (maxObjectSize > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, maxObjectSize);
        }
        return effectiveMaxUploadSize;
    }
}
