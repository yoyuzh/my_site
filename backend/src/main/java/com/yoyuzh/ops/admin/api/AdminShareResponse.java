package com.yoyuzh.ops.admin.api;

import java.time.LocalDateTime;

public record AdminShareResponse(
        Long id,
        String token,
        String shareName,
        boolean passwordProtected,
        boolean expired,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        Integer maxDownloads,
        long downloadCount,
        long viewCount,
        boolean allowImport,
        boolean allowDownload,
        Long ownerId,
        String ownerUsername,
        String ownerEmail,
        Long fileId,
        String fileName,
        String filePath,
        String fileContentType,
        long fileSize,
        boolean directory
) {
}
