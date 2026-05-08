package com.yoyuzh.files.workspace.api;

import java.util.List;

public record WorkspaceArchiveListing(
        List<WorkspaceArchiveEntry> entries,
        String commonRootDirectoryName
) {
}
