package com.yoyuzh.files.search;

import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.search.api.FileSearchApi;
import com.yoyuzh.files.search.api.SearchFilesQuery;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileSearchService {

    private final FileSearchApi fileSearchApi;

    @Autowired
    public FileSearchService(FileSearchApi fileSearchApi) {
        this.fileSearchApi = fileSearchApi;
    }

    public PageResponse<FileMetadataResponse> search(Long userId, FileSearchQuery query) {
        return fileSearchApi.search(
                userId,
                new SearchFilesQuery(
                        query.name(),
                        query.directory(),
                        query.sizeGte(),
                        query.sizeLte(),
                        query.createdGte(),
                        query.createdLte(),
                        query.updatedGte(),
                        query.updatedLte(),
                        query.page(),
                        query.size()
                )
        );
    }
}
