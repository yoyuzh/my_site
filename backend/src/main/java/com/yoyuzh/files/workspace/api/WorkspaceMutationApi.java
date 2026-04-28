package com.yoyuzh.files.workspace.api;

public interface WorkspaceMutationApi {

    WorkspaceMutationResult rename(Long userId, Long fileId, String sanitizedFilename);

    WorkspaceMoveResult move(Long userId, Long fileId, String normalizedTargetPath, WorkspaceMoveConflictStrategy conflictStrategy);
}
