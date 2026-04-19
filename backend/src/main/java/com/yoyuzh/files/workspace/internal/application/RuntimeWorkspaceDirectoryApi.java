package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
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
                                        FileContentStorage fileContentStorage) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
        this.workspacePathPolicy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
    }

    @Override
    public FileMetadataResponse createDirectory(User user, String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "根目录无需创建");
        }
        String parentPath = workspacePathPolicy.extractParentPath(normalizedPath);
        String directoryName = workspacePathPolicy.extractLeafName(normalizedPath);
        workspacePathPolicy.ensureNodeNameAvailable(user.getId(), parentPath, directoryName, "目录已存在");

        fileContentStorage.createDirectory(user.getId(), normalizedPath);

        StoredFile storedFile = new StoredFile();
        storedFile.setUser(user);
        storedFile.setFilename(directoryName);
        storedFile.setPath(parentPath);
        storedFile.setLegacyStorageName(directoryName);
        storedFile.setContentType("directory");
        storedFile.setSize(0L);
        storedFile.setDirectory(true);
        return toResponse(storedFileRepository.save(storedFile));
    }

    @Override
    public PageResponse<FileMetadataResponse> loadDirectoryPage(User user, String normalizedPath, int page, int size) {
        Page<StoredFile> result = storedFileRepository.findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(
                user.getId(),
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
                storedFile.getCreatedAt()
        );
    }
}
