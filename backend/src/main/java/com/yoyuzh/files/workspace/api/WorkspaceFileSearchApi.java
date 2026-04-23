package com.yoyuzh.files.workspace.api;

import com.yoyuzh.shared.kernel.PageResponse;

public interface WorkspaceFileSearchApi {

    PageResponse<FileMetadataResponse> search(Long userId, WorkspaceFileSearchQuery query);
}
