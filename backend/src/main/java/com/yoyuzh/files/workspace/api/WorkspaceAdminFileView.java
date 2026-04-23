package com.yoyuzh.files.workspace.api;

import java.time.LocalDateTime;

public record WorkspaceAdminFileView(
        Long fileId,
        String filename,
        String path,
        Long size,
        String contentType,
        boolean directory,
        LocalDateTime createdAt,
        Long ownerUserId,
        String ownerUsername,
        String ownerEmail,
        boolean favorite,
        boolean thumbnailAvailable
) {
}
