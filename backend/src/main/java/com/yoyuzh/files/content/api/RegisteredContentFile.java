package com.yoyuzh.files.content.api;

import java.time.LocalDateTime;

public record RegisteredContentFile(
        Long id,
        String filename,
        String path,
        long size,
        String contentType,
        boolean directory,
        LocalDateTime createdAt
) {
}
