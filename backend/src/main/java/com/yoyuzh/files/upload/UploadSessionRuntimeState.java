package com.yoyuzh.files.upload;

import java.time.LocalDateTime;

public record UploadSessionRuntimeState(
        String phase,
        long uploadedBytes,
        int uploadedPartCount,
        Integer progressPercent,
        LocalDateTime lastUpdatedAt,
        LocalDateTime expiresAt
) {
}
