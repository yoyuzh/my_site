package com.yoyuzh.files.upload.api;

import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;

public record ValidatedUploadTarget(
        String normalizedPath,
        String filename,
        DefaultStoragePolicySnapshot defaultPolicySnapshot
) {
}
