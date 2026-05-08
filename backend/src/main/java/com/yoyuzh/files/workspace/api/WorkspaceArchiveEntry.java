package com.yoyuzh.files.workspace.api;

public record WorkspaceArchiveEntry(
        String relativePath,
        boolean directory,
        long size,
        String contentType
) {
}
