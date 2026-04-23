package com.yoyuzh.files.workspace.api;

import java.time.LocalDateTime;

public record FileDetailResponse(
        Long id,
        String filename,
        String path,
        long size,
        String contentType,
        boolean directory,
        boolean favorite,
        boolean shared,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
