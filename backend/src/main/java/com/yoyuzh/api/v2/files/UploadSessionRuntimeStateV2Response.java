package com.yoyuzh.api.v2.files;

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
