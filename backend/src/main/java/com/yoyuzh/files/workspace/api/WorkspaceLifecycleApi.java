package com.yoyuzh.files.workspace.api;

import com.yoyuzh.auth.User;

public interface WorkspaceLifecycleApi {

    WorkspaceLifecycleResult copy(User user, Long fileId, String normalizedTargetPath, WorkspaceQuotaGuard quotaGuard);

    WorkspaceLifecycleResult recycle(User user, Long fileId);

    WorkspaceLifecycleResult restore(User user, Long fileId, WorkspaceQuotaGuard quotaGuard);
}
