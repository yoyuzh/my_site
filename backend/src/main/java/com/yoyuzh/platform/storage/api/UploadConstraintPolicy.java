package com.yoyuzh.platform.storage.api;

public interface UploadConstraintPolicy {

    long resolveEffectiveMaxUploadSize(long systemMaxFileSize,
                                       long userMaxUploadSizeBytes,
                                       long policyMaxSizeBytes,
                                       long maxObjectSize);
}
