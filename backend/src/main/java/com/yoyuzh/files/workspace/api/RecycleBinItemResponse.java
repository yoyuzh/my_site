package com.yoyuzh.files.workspace.api;

import java.time.LocalDateTime;

public record RecycleBinItemResponse(
        Long id,
        String filename,
        String path,
        Long size,
        String contentType,
        boolean directory,
        LocalDateTime createdAt,
        LocalDateTime deletedAt,
        LocalDateTime expiresAt
) {
}
