package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.files.upload.api.UploadSessionUploadMode;
import com.yoyuzh.files.upload.internal.domain.UploadSessionStatus;

import java.time.LocalDateTime;

public record UploadSessionView(
        String sessionId,
        String objectKey,
        String targetPath,
        String filename,
        String contentType,
        Long size,
        Long storagePolicyId,
        UploadSessionStatus status,
        Long chunkSize,
        Integer chunkCount,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UploadSessionRuntimeState runtimeState,
        UploadSessionUploadMode uploadMode,
        boolean tusBacked
) {
}
