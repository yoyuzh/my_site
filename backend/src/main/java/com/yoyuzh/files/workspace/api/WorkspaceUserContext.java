package com.yoyuzh.files.workspace.api;

public record WorkspaceUserContext(
        Long userId,
        Long storageQuotaBytes,
        Long maxUploadSizeBytes
) {
}
