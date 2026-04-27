package com.yoyuzh.transfer.api;

import com.yoyuzh.transfer.internal.domain.RemoteDownloadSourceType;

public record CreateRemoteDownloadCommand(
        RemoteDownloadSourceType sourceType,
        String sourceValue,
        String torrentFilename,
        byte[] torrentContent,
        String targetPath
) {
}
