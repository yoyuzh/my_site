package com.yoyuzh.files.search.api;

import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;

public interface FileSearchApi {

    PageResponse<FileMetadataResponse> search(Long userId, SearchFilesQuery query);
}
