package com.yoyuzh.platform.storage.api;

import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;

public record DefaultStoragePolicySnapshot(StoragePolicy policy, StoragePolicyCapabilities capabilities) {
}
