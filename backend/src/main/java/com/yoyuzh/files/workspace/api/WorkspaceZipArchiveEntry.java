package com.yoyuzh.files.workspace.api;

public record WorkspaceZipArchiveEntry(
        String relativePath,
        boolean directory,
        byte[] content
) {
}
