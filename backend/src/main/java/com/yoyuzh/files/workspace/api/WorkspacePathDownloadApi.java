package com.yoyuzh.files.workspace.api;

public interface WorkspacePathDownloadApi {

    WorkspaceDownloadResult downloadOwnedFileByPath(Long userId, String normalizedLogicalPath);

    WorkspaceDownloadStreamResult streamOwnedFileByPath(Long userId, String normalizedLogicalPath);
}
