package com.yoyuzh.files.workspace.api;

public interface WorkspacePathWriteApi {

    FileMetadataResponse createDirectoryByPath(Long userId, String normalizedLogicalPath);

    FileMetadataResponse putFileByPath(WebDavWorkspacePutCommand command);

    WorkspaceLifecycleResult copyByPath(Long userId,
                                        String fromLogicalPath,
                                        String toLogicalPath,
                                        WorkspaceQuotaGuard quotaGuard);

    WorkspaceMoveResult moveByPath(Long userId, String fromLogicalPath, String toLogicalPath, boolean overwrite);

    WorkspaceLifecycleResult recycleByPath(Long userId, String normalizedLogicalPath);
}
