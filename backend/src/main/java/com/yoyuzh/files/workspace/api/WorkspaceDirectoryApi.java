package com.yoyuzh.files.workspace.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.FileMetadataResponse;

public interface WorkspaceDirectoryApi {

    FileMetadataResponse createDirectory(User user, String normalizedPath);

    PageResponse<FileMetadataResponse> loadDirectoryPage(User user, String normalizedPath, int page, int size);
}
