package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceFileSearchApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSearchQuery;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RuntimeWorkspaceFileSearchApi implements WorkspaceFileSearchApi {

    private final StoredFileRepository storedFileRepository;

    @Override
    public PageResponse<FileMetadataResponse> search(Long userId, WorkspaceFileSearchQuery query) {
        Page<StoredFile> result = storedFileRepository.searchUserFiles(
                userId,
                query.name(),
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
