package com.yoyuzh.files.workspace.api;

import java.time.LocalDateTime;

public record WorkspaceFileSnapshot(
        Long id,
        Long userId,
        String filename,
        String path,
        Long size,
        String contentType,
        boolean directory,
        Long blobId,
        LocalDateTime createdAt
) {
}
