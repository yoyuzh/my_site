package com.yoyuzh.transfer.api;

import java.time.Instant;

public record RemoteDownloadListItemResponse(
        Long id,
        Long backgroundTaskId,
        String status,
        String sourceType,
        String engineType,
        String targetPath,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {
}
