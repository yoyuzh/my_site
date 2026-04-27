package com.yoyuzh.platform.storage.api;

import java.io.InputStream;

public interface StoragePolicyBlobAccessApi {

    void validateMigration(StoragePolicyDescriptor sourcePolicy, StoragePolicyDescriptor targetPolicy);

    byte[] readBlob(StoragePolicyDescriptor policy, String objectKey);

    InputStream openBlobStream(StoragePolicyDescriptor policy, String objectKey);

    void storeBlob(StoragePolicyDescriptor policy, String objectKey, String contentType, byte[] content);

    void storeBlob(StoragePolicyDescriptor policy, String objectKey, String contentType, InputStream content, long size);

    void deleteBlob(StoragePolicyDescriptor policy, String objectKey);
}
