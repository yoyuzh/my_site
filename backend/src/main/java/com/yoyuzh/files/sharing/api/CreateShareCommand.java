package com.yoyuzh.files.sharing.api;

import java.time.LocalDateTime;

public record CreateShareCommand(
        Long fileId,
        String password,
        String shareName,
        Boolean allowImport,
        Boolean allowDownload,
        LocalDateTime expiresAt,
        Integer maxDownloads
) {
}
