package com.yoyuzh.files.upload.internal.web;

import java.time.LocalDateTime;

public record UploadSessionRuntimeStateV2Response(
        String phase,
        long uploadedBytes,
        int uploadedPartCount,
        Integer progressPercent,
        LocalDateTime lastUpdatedAt,
        LocalDateTime expiresAt
) {
}
