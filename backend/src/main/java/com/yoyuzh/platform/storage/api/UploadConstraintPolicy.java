package com.yoyuzh.platform.storage.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;

public interface UploadConstraintPolicy {

    long resolveEffectiveMaxUploadSize(long systemMaxFileSize,
                                       User user,
                                       StoragePolicy policy,
                                       StoragePolicyCapabilities capabilities);
}
