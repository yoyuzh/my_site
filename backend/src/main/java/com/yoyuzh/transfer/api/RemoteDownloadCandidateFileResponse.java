package com.yoyuzh.transfer.api;

public record RemoteDownloadCandidateFileResponse(
        String fileKey,
        String relativePath,
        long size,
        boolean selected
) {
}
