package com.yoyuzh.files.content.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.core.FileBlob;

public record ContentRegistrationCommand(
        User user,
        String normalizedPath,
        String filename,
        String contentType,
        long size,
        FileBlob blob
) {
}
