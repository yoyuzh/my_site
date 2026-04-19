package com.yoyuzh.files.search;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.search.api.FileSearchApi;
import com.yoyuzh.files.search.api.SearchFilesQuery;
import com.yoyuzh.files.search.internal.application.RuntimeFileSearchApi;
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

    FileSearchService(StoredFileRepository storedFileRepository) {
        this(new RuntimeFileSearchApi(storedFileRepository));
    }

    public PageResponse<FileMetadataResponse> search(User user, FileSearchQuery query) {
        return fileSearchApi.search(
                user,
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
