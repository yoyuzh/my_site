package com.yoyuzh.files.sharing.api;

import java.time.LocalDateTime;

public record CreateFileShareLinkResponse(
        String token,
        String filename,
        long size,
        String contentType,
        LocalDateTime createdAt
) {
}
