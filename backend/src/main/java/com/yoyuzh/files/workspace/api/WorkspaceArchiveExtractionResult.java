package com.yoyuzh.files.workspace.api;

public record WorkspaceArchiveExtractionResult(
        String extractedPath,
        int extractedFileCount,
        int extractedDirectoryCount
) {
}
