package com.yoyuzh.files.workspace.api;

public interface WorkspaceMutationApi {

    WorkspaceMutationResult rename(Long userId, Long fileId, String sanitizedFilename);

    WorkspaceMutationResult move(Long userId, Long fileId, String normalizedTargetPath);
}
