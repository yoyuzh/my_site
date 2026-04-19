package com.yoyuzh.platform.storage.api;

import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.upload.UploadSessionUploadMode;

public interface UploadModePolicy {

    UploadSessionUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities);
}
