package com.yoyuzh.files.sharing.internal.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateShareV2Request(
        @NotNull Long fileId,
        String password,
        LocalDateTime expiresAt,
        @Min(1) Integer maxDownloads,
        Boolean allowImport,
        Boolean allowDownload,
        String shareName
) {
}
