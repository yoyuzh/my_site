package com.yoyuzh.files.search.internal.application;

import com.yoyuzh.api.v2.ApiV2ErrorCode;
import com.yoyuzh.api.v2.ApiV2Exception;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.FileMetadataResponse;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.search.api.FileSearchApi;
import com.yoyuzh.files.search.api.SearchFilesQuery;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeFileSearchApi implements FileSearchApi {

    private static final int MAX_PAGE_SIZE = 100;

    private final StoredFileRepository storedFileRepository;

    public RuntimeFileSearchApi(StoredFileRepository storedFileRepository) {
        this.storedFileRepository = storedFileRepository;
    }

    @Override
    public PageResponse<FileMetadataResponse> search(User user, SearchFilesQuery query) {
        validateQuery(query);
        Page<StoredFile> result = storedFileRepository.searchUserFiles(
                user.getId(),
                normalizeName(query.name()),
                query.directory(),
                query.sizeGte(),
                query.sizeLte(),
                query.createdGte(),
                query.createdLte(),
                query.updatedGte(),
                query.updatedLte(),
                PageRequest.of(query.page(), query.size())
        );
        List<FileMetadataResponse> items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getTotalElements(), query.page(), query.size());
    }

    private void validateQuery(SearchFilesQuery query) {
        if (query.page() < 0) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "分页页码不能小于 0");
        }
        if (query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "分页大小必须在 1 到 100 之间");
        }
        if (query.sizeGte() != null && query.sizeGte() < 0) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "文件大小下限不能小于 0");
        }
        if (query.sizeLte() != null && query.sizeLte() < 0) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "文件大小上限不能小于 0");
        }
        if (query.sizeGte() != null && query.sizeLte() != null && query.sizeGte() > query.sizeLte()) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "文件大小范围不合法");
        }
        if (query.createdGte() != null && query.createdLte() != null && query.createdGte().isAfter(query.createdLte())) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "创建时间范围不合法");
        }
        if (query.updatedGte() != null && query.updatedLte() != null && query.updatedGte().isAfter(query.updatedLte())) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "更新时间范围不合法");
        }
    }

    private String normalizeName(String name) {
        return StringUtils.hasText(name) ? name.trim() : null;
    }

    private FileMetadataResponse toResponse(StoredFile storedFile) {
        String logicalPath = storedFile.isDirectory()
                ? buildLogicalPath(storedFile)
                : storedFile.getPath();
        return new FileMetadataResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                logicalPath,
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt()
        );
    }

    private String buildLogicalPath(StoredFile storedFile) {
        return "/".equals(storedFile.getPath())
                ? "/" + storedFile.getFilename()
                : storedFile.getPath() + "/" + storedFile.getFilename();
    }
}
