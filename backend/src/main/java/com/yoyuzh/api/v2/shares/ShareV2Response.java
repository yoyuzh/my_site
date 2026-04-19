package com.yoyuzh.api.v2.shares;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;

import java.time.LocalDateTime;

public record ShareV2Response(
        Long id,
        String token,
        String shareName,
        String ownerUsername,
        boolean passwordRequired,
        boolean passwordVerified,
        boolean allowImport,
        boolean allowDownload,
        Integer maxDownloads,
        long downloadCount,
        long viewCount,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        FileMetadataResponse file
) {
}
