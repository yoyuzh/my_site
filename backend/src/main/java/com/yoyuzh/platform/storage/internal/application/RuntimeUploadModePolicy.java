package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StorageUploadMode;
import com.yoyuzh.platform.storage.api.UploadModePolicy;
import org.springframework.stereotype.Service;

@Service
public class RuntimeUploadModePolicy implements UploadModePolicy {

    @Override
    public StorageUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities) {
        if (!capabilities.directUpload()) {
            return StorageUploadMode.PROXY;
        }
        if (capabilities.multipartUpload()) {
            return StorageUploadMode.DIRECT_MULTIPART;
        }
        return StorageUploadMode.DIRECT_SINGLE;
    }
}
