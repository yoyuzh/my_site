package com.yoyuzh.files.upload.api;

public interface UploadTargetPolicy {

    ValidatedUploadTarget validateUpload(Long userId,
                                         Long maxUploadSizeBytes,
                                         long storageQuotaBytes,
                                         String path,
                                         String filename,
                                         long size);
}
