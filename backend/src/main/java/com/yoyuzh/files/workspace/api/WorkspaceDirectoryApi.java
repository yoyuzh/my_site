package com.yoyuzh.files.workspace.api;

import com.yoyuzh.shared.kernel.PageResponse;

public interface WorkspaceDirectoryApi {

    FileMetadataResponse createDirectory(Long userId, String normalizedPath);

    PageResponse<FileMetadataResponse> loadDirectoryPage(Long userId, String normalizedPath, int page, int size);
}
