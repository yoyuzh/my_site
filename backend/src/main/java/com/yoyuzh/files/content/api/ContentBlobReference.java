package com.yoyuzh.files.content.api;

public record ContentBlobReference(
        Long blobId,
        String objectKey,
        String contentType,
        long size
) {
}
