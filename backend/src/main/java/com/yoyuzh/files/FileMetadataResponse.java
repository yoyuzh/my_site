package com.yoyuzh.files;

import java.time.LocalDateTime;

public record FileMetadataResponse(
        Long id,
        String filename,
        String path,
        long size,
        String contentType,
        boolean directory,
        LocalDateTime createdAt
) {
}
