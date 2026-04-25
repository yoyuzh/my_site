package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

public final class RuntimeWorkspaceDirectoryApi implements WorkspaceDirectoryApi {

    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;
    private final WorkspacePathPolicy workspacePathPolicy;

    public RuntimeWorkspaceDirectoryApi(StoredFileRepository storedFileRepository,
                                        FileContentStorage fileContentStorage,
                                        WorkspacePathPolicy workspacePathPolicy) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
        this.workspacePathPolicy = workspacePathPolicy;
    }

    public RuntimeWorkspaceDirectoryApi(StoredFileRepository storedFileRepository,
                                        FileContentStorage fileContentStorage) {
        this(storedFileRepository, fileContentStorage, new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage));
    }

    @Override
    public FileMetadataResponse createDirectory(Long userId, String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "根目录无需创建");
        }
        String parentPath = workspacePathPolicy.extractParentPath(normalizedPath);
        String directoryName = workspacePathPolicy.extractLeafName(normalizedPath);
        workspacePathPolicy.ensureNodeNameAvailable(userId, parentPath, directoryName, "目录已存在");

        fileContentStorage.createDirectory(userId, normalizedPath);

        StoredFile storedFile = new StoredFile();
        storedFile.setUserId(userId);
        storedFile.setFilename(directoryName);
        storedFile.setPath(parentPath);
        storedFile.setLegacyStorageName(directoryName);
        storedFile.setContentType("directory");
        storedFile.setSize(0L);
        storedFile.setDirectory(true);
        return toResponse(storedFileRepository.save(storedFile));
    }

    @Override
    public PageResponse<FileMetadataResponse> loadDirectoryPage(Long userId, String normalizedPath, int page, int size) {
        Page<StoredFile> result = storedFileRepository.findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(
                userId,
                normalizedPath,
                PageRequest.of(page, size)
        );
        return new PageResponse<>(result.getContent().stream().map(this::toResponse).toList(), result.getTotalElements(), page, size);
    }

    private FileMetadataResponse toResponse(StoredFile storedFile) {
        return new FileMetadataResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                storedFile.getPath(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt(),
                storedFile.getUpdatedAt() != null ? storedFile.getUpdatedAt() : storedFile.getCreatedAt()
        );
    }
}
