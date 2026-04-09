package com.yoyuzh.files.share;

import java.time.LocalDateTime;

public record FileShareDetailsResponse(
        String token,
        String ownerUsername,
        String filename,
        long size,
        String contentType,
        boolean directory,
        LocalDateTime createdAt
) {
}
