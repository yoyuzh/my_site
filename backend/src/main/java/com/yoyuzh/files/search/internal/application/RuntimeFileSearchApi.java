package com.yoyuzh.files.search.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.search.api.FileSearchApi;
import com.yoyuzh.files.search.api.SearchFilesQuery;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceFileSearchApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RuntimeFileSearchApi implements FileSearchApi {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorkspaceFileSearchApi workspaceFileSearchApi;

    @Override
    public PageResponse<FileMetadataResponse> search(Long userId, SearchFilesQuery query) {
        validateQuery(query);
        return workspaceFileSearchApi.search(userId, new WorkspaceFileSearchQuery(
                normalizeName(query.name()),
                query.directory(),
                query.sizeGte(),
                query.sizeLte(),
                query.createdGte(),
                query.createdLte(),
                query.updatedGte(),
                query.updatedLte(),
                query.page(),
                query.size()
        ));
    }

    private void validateQuery(SearchFilesQuery query) {
        if (query.page() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "分页页码不能小于 0");
        }
        if (query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "分页大小必须在 1 到 100 之间");
        }
        if (query.sizeGte() != null && query.sizeGte() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "文件大小下限不能小于 0");
        }
        if (query.sizeLte() != null && query.sizeLte() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "文件大小上限不能小于 0");
        }
        if (query.sizeGte() != null && query.sizeLte() != null && query.sizeGte() > query.sizeLte()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "文件大小范围不合法");
        }
        if (query.createdGte() != null && query.createdLte() != null && query.createdGte().isAfter(query.createdLte())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "创建时间范围不合法");
        }
        if (query.updatedGte() != null && query.updatedLte() != null && query.updatedGte().isAfter(query.updatedLte())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "更新时间范围不合法");
        }
    }

    private String normalizeName(String name) {
        return StringUtils.hasText(name) ? name.trim() : null;
    }

}
