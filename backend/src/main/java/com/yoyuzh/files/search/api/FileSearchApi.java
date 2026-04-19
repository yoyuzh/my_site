package com.yoyuzh.files.search.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;

public interface FileSearchApi {

    PageResponse<FileMetadataResponse> search(User user, SearchFilesQuery query);
}
