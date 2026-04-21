package com.yoyuzh.files.content.api;

public record ContentRegistrationCommand(
        Long userId,
        String normalizedPath,
        String filename,
        String contentType,
        long size,
        ContentBlobReference blob
) {
}
