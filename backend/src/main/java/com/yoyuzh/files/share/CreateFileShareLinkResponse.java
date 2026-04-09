package com.yoyuzh.files.share;

import java.time.LocalDateTime;

public record CreateFileShareLinkResponse(
        String token,
        String filename,
        long size,
        String contentType,
        LocalDateTime createdAt
) {
}
