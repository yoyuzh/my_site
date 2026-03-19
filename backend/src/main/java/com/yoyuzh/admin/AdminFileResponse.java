package com.yoyuzh.admin;

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
        String ownerEmail
) {
}
