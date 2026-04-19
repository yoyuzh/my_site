package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.upload.UploadSessionUploadMode;
import com.yoyuzh.platform.storage.api.UploadModePolicy;
import org.springframework.stereotype.Service;

@Service
public class RuntimeUploadModePolicy implements UploadModePolicy {

    @Override
    public UploadSessionUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities) {
        if (!capabilities.directUpload()) {
            return UploadSessionUploadMode.PROXY;
        }
        if (capabilities.multipartUpload()) {
            return UploadSessionUploadMode.DIRECT_MULTIPART;
        }
        return UploadSessionUploadMode.DIRECT_SINGLE;
    }
}
