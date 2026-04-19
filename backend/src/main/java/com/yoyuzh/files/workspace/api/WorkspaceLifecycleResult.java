package com.yoyuzh.files.workspace.api;

import java.util.List;

public record WorkspaceLifecycleResult(
        FileMetadataResponse file,
        String fromPath,
        String toPath,
        List<String> affectedPaths
) {
}
