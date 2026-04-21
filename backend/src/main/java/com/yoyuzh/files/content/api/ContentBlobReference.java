package com.yoyuzh.files.content.api;

public record ContentBlobReference(
        String objectKey,
        String contentType,
        long size
) {
}
