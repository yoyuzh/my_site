package com.yoyuzh.platform.storage.api;

public interface StoragePolicyQuery {

    DefaultStoragePolicySnapshot readDefaultPolicySnapshot();

    default Long readDefaultPolicyId() {
        return readDefaultPolicySnapshot().policy().getId();
    }
}
