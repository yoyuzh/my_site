package com.yoyuzh.transfer.api;

public record CreateRemoteDownloadCommand(
        RemoteDownloadSourceType sourceType,
        String sourceValue,
        String torrentFilename,
        byte[] torrentContent,
        String targetPath
) {
}
