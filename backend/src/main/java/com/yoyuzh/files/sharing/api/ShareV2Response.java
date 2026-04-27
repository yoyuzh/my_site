package com.yoyuzh.files.sharing.api;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;

import java.time.LocalDateTime;

public record ShareV2Response(
        Long id,
        String token,
        String shareName,
        String ownerUsername,
        String password,
        boolean passwordRequired,
        boolean passwordVerified,
        boolean allowImport,
        boolean allowDownload,
        boolean expireAfterConsume,
        Integer maxDownloads,
        long downloadCount,
        long viewCount,
        ShareStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        FileMetadataResponse file
) {
}
