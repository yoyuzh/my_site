package com.yoyuzh.files.workspace.api;

public record WorkspaceContentBindingFile(
        Long fileId,
        Long userId,
        String path,
        String legacyStorageName,
        String contentType,
        Long size,
        Long blobId
) {
}
