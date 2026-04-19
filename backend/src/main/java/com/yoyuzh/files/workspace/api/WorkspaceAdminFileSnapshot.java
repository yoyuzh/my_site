package com.yoyuzh.files.workspace.api;

public record WorkspaceAdminFileSnapshot(
        Long fileId,
        Long ownerUserId,
        String path,
        String filename,
        boolean directory
) {
}
