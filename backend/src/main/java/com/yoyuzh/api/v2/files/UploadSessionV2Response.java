package com.yoyuzh.api.v2.files;

import java.time.LocalDateTime;

public record UploadSessionV2Response(
        String sessionId,
        String objectKey,
        boolean directUpload,
        boolean multipartUpload,
        String uploadMode,
        String path,
        String filename,
        String contentType,
        long size,
        Long storagePolicyId,
        String status,
        long chunkSize,
        int chunkCount,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UploadSessionRuntimeStateV2Response runtime,
        UploadSessionV2StrategyResponse strategy
) {
}
