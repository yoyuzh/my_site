package com.yoyuzh.files.workspace.api;

import com.yoyuzh.files.core.FileMetadataResponse;

import java.util.List;

public record WorkspaceLifecycleResult(
        FileMetadataResponse file,
        String fromPath,
        String toPath,
        List<String> affectedPaths
) {
}
