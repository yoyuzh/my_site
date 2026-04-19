package com.yoyuzh.files.upload.api;

import com.yoyuzh.auth.User;

public record UploadCompletionCommand(
        User user,
        String normalizedPath,
        String filename,
        String objectKey,
        String contentType,
        long size
) {
}
