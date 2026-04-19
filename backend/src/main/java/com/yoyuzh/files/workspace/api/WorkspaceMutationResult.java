package com.yoyuzh.files.workspace.api;

import java.util.List;

public record WorkspaceMutationResult(
        FileMetadataResponse file,
        String fromPath,
        String toPath,
        List<String> affectedPaths
) {
}
