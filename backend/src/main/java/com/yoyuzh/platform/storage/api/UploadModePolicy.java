package com.yoyuzh.platform.storage.api;

public interface UploadModePolicy {

    StorageUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities);
}
