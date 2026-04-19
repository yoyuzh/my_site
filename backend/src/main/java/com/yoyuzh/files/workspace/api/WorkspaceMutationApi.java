package com.yoyuzh.files.workspace.api;

import com.yoyuzh.auth.User;

public interface WorkspaceMutationApi {

    WorkspaceMutationResult rename(User user, Long fileId, String sanitizedFilename);

    WorkspaceMutationResult move(User user, Long fileId, String normalizedTargetPath);
}
