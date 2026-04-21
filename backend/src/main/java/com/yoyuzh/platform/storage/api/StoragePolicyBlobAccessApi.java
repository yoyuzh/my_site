package com.yoyuzh.platform.storage.api;

public interface StoragePolicyBlobAccessApi {

    void validateMigration(StoragePolicyDescriptor sourcePolicy, StoragePolicyDescriptor targetPolicy);

    byte[] readBlob(StoragePolicyDescriptor policy, String objectKey);

    void storeBlob(StoragePolicyDescriptor policy, String objectKey, String contentType, byte[] content);

    void deleteBlob(StoragePolicyDescriptor policy, String objectKey);
}
