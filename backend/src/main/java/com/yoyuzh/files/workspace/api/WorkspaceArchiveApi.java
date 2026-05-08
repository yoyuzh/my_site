package com.yoyuzh.files.workspace.api;

public interface WorkspaceArchiveApi {

    WorkspaceArchiveSummary summarizeArchiveSource(Long userId, Long fileId);

    byte[] buildArchiveBytes(Long userId, Long fileId, WorkspaceArchiveBuildProgressListener progressListener);

    WorkspaceArchiveListing readArchive(Long userId, Long fileId);

    WorkspaceArchiveExtractionResult extractArchive(WorkspaceUserContext user,
                                                   Long fileId,
                                                   String outputPath,
                                                   String outputDirectoryName,
                                                   WorkspaceExternalImportProgressListener progressListener);

    WorkspaceZipArchive readZipCompatibleArchive(Long userId, Long fileId);

    WorkspaceArchiveExtractionResult extractZipCompatibleArchive(WorkspaceUserContext user,
                                                                Long fileId,
                                                                String outputPath,
                                                                String outputDirectoryName,
                                                                WorkspaceExternalImportProgressListener progressListener);
}
