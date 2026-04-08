package com.yoyuzh.api.v2.files;

import java.time.LocalDateTime;

public record UploadSessionV2Response(
        String sessionId,
        String objectKey,
        String path,
        String filename,
        String contentType,
        long size,
        String status,
        long chunkSize,
        int chunkCount,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
