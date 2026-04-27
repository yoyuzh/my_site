package com.yoyuzh.transfer.api;

import java.time.Instant;
import java.util.List;

public record RemoteDownloadDetailResponse(
        Long id,
        Long backgroundTaskId,
        String status,
        String sourceType,
        String engineType,
        String targetPath,
        String sourceValue,
        String downloadNodeId,
        int selectedFileCount,
        int importedFileCount,
        String failureCode,
        String failureMessage,
        List<RemoteDownloadCandidateFileResponse> candidateFiles,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {
}
