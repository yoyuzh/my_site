package com.yoyuzh.files.workspace.api;

public interface WorkspaceLifecycleApi {

    WorkspaceLifecycleResult copy(Long userId, Long fileId, String normalizedTargetPath, WorkspaceQuotaGuard quotaGuard);

    WorkspaceLifecycleResult recycle(Long userId, Long fileId);

    WorkspaceLifecycleResult restore(Long userId, Long fileId, WorkspaceQuotaGuard quotaGuard);
}
