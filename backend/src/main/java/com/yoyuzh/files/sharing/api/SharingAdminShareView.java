package com.yoyuzh.files.sharing.api;

import java.time.LocalDateTime;

public record SharingAdminShareView(
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
        Long ownerUserId,
        String ownerUsername,
        String ownerEmail,
        Long fileId,
        String fileName,
        String filePath,
        String fileContentType,
        Long fileSize,
        boolean directory
) {
}
