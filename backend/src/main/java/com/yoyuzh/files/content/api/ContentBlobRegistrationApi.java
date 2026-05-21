package com.yoyuzh.files.content.api;

public interface ContentBlobRegistrationApi {

    ContentBlobReference registerStoredBlob(String objectKey, String contentType, long size);

    ContentBlobReference registerPendingBlob(String objectKey, String contentType, long size, String localTempPath);

    void attachUploadTask(Long blobId, Long uploadTaskId);

    void markBlobReady(Long blobId);

    void markBlobFailed(Long blobId);
}
