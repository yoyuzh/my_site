package com.yoyuzh.files.workspace.internal.web;

import java.time.LocalDateTime;

public record RecycleBinItemResponse(
        Long id,
        String filename,
        String path,
        long size,
        String contentType,
        boolean directory,
        LocalDateTime createdAt,
        LocalDateTime deletedAt,
        LocalDateTime expiresAt
) {
}
