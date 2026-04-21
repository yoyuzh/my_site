package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceMutationApi;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceMutationResult;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;

import java.util.List;

public final class RuntimeWorkspaceMutationApi implements WorkspaceMutationApi {

    private final StoredFileRepository storedFileRepository;
    private final WorkspacePathPolicy workspacePathPolicy;

    public RuntimeWorkspaceMutationApi(StoredFileRepository storedFileRepository,
                                       WorkspacePathPolicy workspacePathPolicy) {
        this.storedFileRepository = storedFileRepository;
        this.workspacePathPolicy = workspacePathPolicy;
    }

    public RuntimeWorkspaceMutationApi(StoredFileRepository storedFileRepository,
                                       FileContentStorage fileContentStorage) {
        this(storedFileRepository, new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage));
    }

    @Override
    public WorkspaceMutationResult rename(Long userId, Long fileId, String sanitizedFilename) {
        StoredFile storedFile = getOwnedActiveFile(userId, fileId, "重命名");
        String fromPath = buildLogicalPath(storedFile);
        if (sanitizedFilename.equals(storedFile.getFilename())) {
            return new WorkspaceMutationResult(toMutationResponse(storedFile), fromPath, fromPath, List.of());
        }
        workspacePathPolicy.ensureNodeNameAvailable(userId, storedFile.getPath(), sanitizedFilename, "同目录下文件已存在");

        if (storedFile.isDirectory()) {
            String oldLogicalPath = buildLogicalPath(storedFile);
            String newLogicalPath = "/".equals(storedFile.getPath())
                    ? "/" + sanitizedFilename
                    : storedFile.getPath() + "/" + sanitizedFilename;

            List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(userId, oldLogicalPath);
            for (StoredFile descendant : descendants) {
                if (descendant.getPath().equals(oldLogicalPath)) {
                    descendant.setPath(newLogicalPath);
                    continue;
                }

                descendant.setPath(newLogicalPath + descendant.getPath().substring(oldLogicalPath.length()));
            }
            if (!descendants.isEmpty()) {
                storedFileRepository.saveAll(descendants);
            }
        }

        storedFile.setFilename(sanitizedFilename);
        StoredFile savedFile = storedFileRepository.save(storedFile);
        return new WorkspaceMutationResult(
                toMutationResponse(savedFile),
                fromPath,
                buildLogicalPath(savedFile),
                List.of(savedFile.getPath())
        );
    }

    @Override
    public WorkspaceMutationResult move(Long userId, Long fileId, String normalizedTargetPath) {
        StoredFile storedFile = getOwnedActiveFile(userId, fileId, "移动");
        String fromPath = buildLogicalPath(storedFile);
        if (normalizedTargetPath.equals(storedFile.getPath())) {
            return new WorkspaceMutationResult(toMutationResponse(storedFile), fromPath, fromPath, List.of());
        }

        workspacePathPolicy.ensureExistingDirectoryPath(userId, normalizedTargetPath);
        workspacePathPolicy.ensureNodeNameAvailable(userId, normalizedTargetPath, storedFile.getFilename(), "目标目录已存在同名文件");

        if (storedFile.isDirectory()) {
            String oldLogicalPath = buildLogicalPath(storedFile);
            String newLogicalPath = "/".equals(normalizedTargetPath)
                    ? "/" + storedFile.getFilename()
                    : normalizedTargetPath + "/" + storedFile.getFilename();
            if (newLogicalPath.equals(oldLogicalPath) || newLogicalPath.startsWith(oldLogicalPath + "/")) {
                throw new BusinessException(ErrorCode.UNKNOWN, "不能移动到当前目录或其子目录");
            }

            List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(userId, oldLogicalPath);
            for (StoredFile descendant : descendants) {
                if (descendant.getPath().equals(oldLogicalPath)) {
                    descendant.setPath(newLogicalPath);
                    continue;
                }

                descendant.setPath(newLogicalPath + descendant.getPath().substring(oldLogicalPath.length()));
            }
            if (!descendants.isEmpty()) {
                storedFileRepository.saveAll(descendants);
            }
        }

        String previousParentPath = storedFile.getPath();
        storedFile.setPath(normalizedTargetPath);
        StoredFile savedFile = storedFileRepository.save(storedFile);
        return new WorkspaceMutationResult(
                toMutationResponse(savedFile),
                fromPath,
                buildLogicalPath(savedFile),
                List.of(previousParentPath, normalizedTargetPath)
        );
    }

    private StoredFile getOwnedActiveFile(Long userId, Long fileId, String action) {
        StoredFile storedFile = storedFileRepository.findDetailedById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!storedFile.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限" + action + "该文件");
        }
        if (storedFile.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }
        return storedFile;
    }

    private String buildLogicalPath(StoredFile storedFile) {
        return "/".equals(storedFile.getPath())
                ? "/" + storedFile.getFilename()
                : storedFile.getPath() + "/" + storedFile.getFilename();
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

    private FileMetadataResponse toMutationResponse(StoredFile storedFile) {
        if (!storedFile.isDirectory()) {
            return toResponse(storedFile);
        }
        return new FileMetadataResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                buildLogicalPath(storedFile),
                storedFile.getSize(),
                storedFile.getContentType(),
                true,
                storedFile.getCreatedAt()
        );
    }
}
