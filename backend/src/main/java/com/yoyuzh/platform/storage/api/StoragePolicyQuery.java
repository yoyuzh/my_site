package com.yoyuzh.platform.storage.api;

public interface StoragePolicyQuery {

    DefaultStoragePolicySnapshot readDefaultPolicySnapshot();

    StoragePolicyDescriptor readPolicyDescriptor(Long policyId);

    StoragePolicyCapabilities readPolicyCapabilities(Long policyId);

    default Long readDefaultPolicyId() {
        return readDefaultPolicySnapshot().policyId();
    }
}
