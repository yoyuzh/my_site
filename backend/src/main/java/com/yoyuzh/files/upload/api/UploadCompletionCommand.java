package com.yoyuzh.files.upload.api;

public record UploadCompletionCommand(
        Long userId,
        String normalizedPath,
        String filename,
        String objectKey,
        String contentType,
        long size
) {
}
