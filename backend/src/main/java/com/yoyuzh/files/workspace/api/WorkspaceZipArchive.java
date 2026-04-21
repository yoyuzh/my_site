package com.yoyuzh.files.workspace.api;

import java.util.List;

public record WorkspaceZipArchive(
        List<WorkspaceZipArchiveEntry> entries,
        String commonRootDirectoryName
) {
}
