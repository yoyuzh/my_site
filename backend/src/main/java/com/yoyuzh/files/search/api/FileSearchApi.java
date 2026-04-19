package com.yoyuzh.files.search.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.FileMetadataResponse;

public interface FileSearchApi {

    PageResponse<FileMetadataResponse> search(User user, SearchFilesQuery query);
}
