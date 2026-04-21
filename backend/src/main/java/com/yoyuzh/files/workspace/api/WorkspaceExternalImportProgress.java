package com.yoyuzh.files.workspace.api;

public record WorkspaceExternalImportProgress(
        int processedFileCount,
        int totalFileCount,
        int processedDirectoryCount,
        int totalDirectoryCount
) {
}
