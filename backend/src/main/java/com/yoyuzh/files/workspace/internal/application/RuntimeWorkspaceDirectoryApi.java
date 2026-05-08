package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RuntimeWorkspaceDirectoryApi implements WorkspaceDirectoryApi {

    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;
    private final WorkspacePathPolicy workspacePathPolicy;
    private final WorkspaceRequestProbe workspaceRequestProbe;

    public RuntimeWorkspaceDirectoryApi(StoredFileRepository storedFileRepository,
                                        FileContentStorage fileContentStorage,
                                        WorkspacePathPolicy workspacePathPolicy,
                                        WorkspaceRequestProbe workspaceRequestProbe) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
        this.workspacePathPolicy = workspacePathPolicy;
        this.workspaceRequestProbe = workspaceRequestProbe == null
                ? WorkspaceRequestProbe.disabled()
                : workspaceRequestProbe;
    }

    public RuntimeWorkspaceDirectoryApi(StoredFileRepository storedFileRepository,
                                        FileContentStorage fileContentStorage,
                                        WorkspacePathPolicy workspacePathPolicy) {
        this(storedFileRepository, fileContentStorage, workspacePathPolicy, WorkspaceRequestProbe.disabled());
    }

    public RuntimeWorkspaceDirectoryApi(StoredFileRepository storedFileRepository,
                                        FileContentStorage fileContentStorage) {
        this(
                storedFileRepository,
                fileContentStorage,
                new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage),
                WorkspaceRequestProbe.disabled()
        );
    }

    @Override
    public FileMetadataResponse createDirectory(Long userId, String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "根目录无需创建");
        }
        String parentPath = workspacePathPolicy.extractParentPath(normalizedPath);
        String requestedDirectoryName = workspacePathPolicy.extractLeafName(normalizedPath);
        String directoryName = workspacePathPolicy.resolveAvailableNodeName(userId, parentPath, requestedDirectoryName);
        String targetPath = workspacePathPolicy.buildTargetLogicalPath(parentPath, directoryName);

        fileContentStorage.createDirectory(userId, targetPath);

        StoredFile storedFile = StoredFile.directory(userId, parentPath, directoryName);
        return toResponse(storedFileRepository.save(storedFile));
    }

    @Override
    public PageResponse<FileMetadataResponse> loadDirectoryPage(Long userId, String normalizedPath, int page, int size) {
        Page<StoredFile> result = workspaceRequestProbe.measure(
                "directory.listQuery",
                () -> storedFileRepository.findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(
                        userId,
                        normalizedPath,
                        PageRequest.of(page, size)
                )
        );
        Set<String> directoryPathsWithChildren = workspaceRequestProbe.measure(
                "directory.childDirQuery",
                () -> loadDirectoryPathsWithChildren(userId, result.getContent())
        );
        return workspaceRequestProbe.measure("directory.responseAssemble", () -> {
            List<FileMetadataResponse> items = result.getContent().stream()
                    .map(storedFile -> toResponse(storedFile, directoryPathsWithChildren.contains(buildLogicalPath(storedFile))))
                    .toList();
            return new PageResponse<>(items, result.getTotalElements(), page, size);
        });
    }

    private FileMetadataResponse toResponse(StoredFile storedFile) {
        return toResponse(storedFile, false);
    }

    private FileMetadataResponse toResponse(StoredFile storedFile, boolean hasChildDirectory) {
        return new FileMetadataResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                storedFile.getPath(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt(),
                storedFile.getUpdatedAt() != null ? storedFile.getUpdatedAt() : storedFile.getCreatedAt(),
                storedFile.getCustomEmoji(),
                storedFile.getFolderColor(),
                hasChildDirectory
        );
    }

    private Set<String> loadDirectoryPathsWithChildren(Long userId, List<StoredFile> storedFiles) {
        List<String> directoryPaths = storedFiles.stream()
                .filter(StoredFile::isDirectory)
                .map(this::buildLogicalPath)
                .distinct()
                .toList();
        if (directoryPaths.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(storedFileRepository.findDirectoryPathsWithChildDirectories(userId, directoryPaths));
    }

    private String buildLogicalPath(StoredFile storedFile) {
        return "/".equals(storedFile.getPath())
                ? "/" + storedFile.getFilename()
                : storedFile.getPath() + "/" + storedFile.getFilename();
    }
}
