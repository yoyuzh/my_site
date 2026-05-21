package com.yoyuzh.files.content.api;

public record ContentBlobStateView(
        Long blobId,
        String objectKey,
        String contentType,
        long size,
        FileBlobStatus status,
        String localTempPath,
        Long uploadTaskId
) {
}
