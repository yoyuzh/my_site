package com.yoyuzh.ops.admin.api;

import java.time.LocalDateTime;

public record AdminFileResponse(
        Long id,
        String filename,
        String path,
        long size,
        String contentType,
        boolean directory,
        LocalDateTime createdAt,
        Long ownerId,
        String ownerUsername,
        String ownerEmail,
        boolean favorite,
        boolean thumbnailAvailable
) {
}
