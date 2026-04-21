package com.yoyuzh.files.workspace.api;

public record WorkspaceArchiveBuildProgress(
        int processedFileCount,
        int totalFileCount,
        int processedDirectoryCount,
        int totalDirectoryCount
) {
}
