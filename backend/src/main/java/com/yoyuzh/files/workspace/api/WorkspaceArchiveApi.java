package com.yoyuzh.files.workspace.api;

public interface WorkspaceArchiveApi {

    WorkspaceArchiveSummary summarizeArchiveSource(Long userId, Long fileId);

    byte[] buildArchiveBytes(Long userId, Long fileId, WorkspaceArchiveBuildProgressListener progressListener);

    WorkspaceZipArchive readZipCompatibleArchive(Long userId, Long fileId);
}
