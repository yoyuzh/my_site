package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WebDavWorkspacePutCommand;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleApi;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleResult;
import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import com.yoyuzh.files.workspace.api.WorkspaceMoveItemResult;
import com.yoyuzh.files.workspace.api.WorkspaceMoveResult;
import com.yoyuzh.files.workspace.api.WorkspaceMutationApi;
import com.yoyuzh.files.workspace.api.WorkspaceMutationResult;
import com.yoyuzh.files.workspace.api.WorkspaceQuotaGuard;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.files.workspace.api.WorkspacePathWriteApi;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RuntimeWorkspacePathWriteApi implements WorkspacePathWriteApi {

    private final StoredFileRepository storedFileRepository;
    private final WorkspaceDirectoryApi workspaceDirectoryApi;
    private final WorkspaceFileIngressService workspaceFileIngressService;
    private final WorkspaceMutationApi workspaceMutationApi;
    private final WorkspaceLifecycleApi workspaceLifecycleApi;
    private final ContentBlobLifecycleApi contentBlobLifecycleApi;
    private final WorkspacePathPolicy workspacePathPolicy;

    public RuntimeWorkspacePathWriteApi(StoredFileRepository storedFileRepository,
                                        WorkspaceDirectoryApi workspaceDirectoryApi,
                                        WorkspaceFileIngressService workspaceFileIngressService,
                                        WorkspaceMutationApi workspaceMutationApi,
                                        WorkspaceLifecycleApi workspaceLifecycleApi,
                                        ContentBlobLifecycleApi contentBlobLifecycleApi,
                                        WorkspacePathPolicy workspacePathPolicy) {
        this.storedFileRepository = storedFileRepository;
        this.workspaceDirectoryApi = workspaceDirectoryApi;
        this.workspaceFileIngressService = workspaceFileIngressService;
        this.workspaceMutationApi = workspaceMutationApi;
        this.workspaceLifecycleApi = workspaceLifecycleApi;
        this.contentBlobLifecycleApi = contentBlobLifecycleApi;
        this.workspacePathPolicy = workspacePathPolicy;
    }

    @Override
    @Transactional
    public FileMetadataResponse createDirectoryByPath(Long userId, String normalizedLogicalPath) {
        String logicalPath = normalizeLogicalPath(normalizedLogicalPath);
        return workspaceDirectoryApi.createDirectory(userId, logicalPath);
    }

    @Override
    @Transactional
    public FileMetadataResponse putFileByPath(WebDavWorkspacePutCommand command) {
        String logicalPath = normalizeLogicalPath(command.normalizedLogicalPath());
        String parentPath = workspacePathPolicy.extractParentPath(logicalPath);
        String filename = workspacePathPolicy.extractLeafName(logicalPath);
        StoredFile existing = findExisting(command.user().userId(), parentPath, filename);
        if (existing == null) {
            return createNewFile(command, parentPath, filename);
        }
        if (!command.overwrite()) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, "目标文件已存在");
        }
        if (existing.isDirectory()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "不能用文件内容覆盖目录");
        }
        return replaceExistingFile(command, existing);
    }

    private FileMetadataResponse createNewFile(WebDavWorkspacePutCommand command, String parentPath, String filename) {
        ArrayList<String> writtenBlobObjectKeys = new ArrayList<>();
        try {
            WorkspaceFileIngressService.CreatedFile createdFile = workspaceFileIngressService.importExternalFile(
                    command.user(),
                    parentPath,
                    filename,
                    command.contentType(),
                    command.size(),
                    command.content(),
                    writtenBlobObjectKeys
            );
            return toResponse(createdFile.file());
        } catch (IOException ex) {
            IllegalStateException wrapped = new IllegalStateException("failed to write WebDAV file content", ex);
            workspaceFileIngressService.cleanupWrittenBlobs(writtenBlobObjectKeys, wrapped);
            throw wrapped;
        }
    }

    @Override
    @Transactional
    public WorkspaceLifecycleResult copyByPath(Long userId,
                                               String fromLogicalPath,
                                               String toLogicalPath,
                                               boolean overwrite,
                                               WorkspaceQuotaGuard quotaGuard) {
        String fromPath = normalizeLogicalPath(fromLogicalPath);
        String toPath = normalizeLogicalPath(toLogicalPath);
        StoredFile source = requireExisting(userId, fromPath);
        String targetParentPath = workspacePathPolicy.extractParentPath(toPath);
        String targetFilename = workspacePathPolicy.extractLeafName(toPath);
        StoredFile target = findExisting(userId, targetParentPath, targetFilename);
        if (target != null && !target.getId().equals(source.getId())) {
            if (!overwrite) {
                throw new BusinessException(ErrorCode.DUPLICATE_NAME, "目标文件已存在");
            }
            workspaceLifecycleApi.recycle(userId, target.getId());
        }
        return workspaceLifecycleApi.copy(userId, source.getId(), targetParentPath, quotaGuard);
    }

    @Override
    @Transactional
    public WorkspaceMoveResult moveByPath(Long userId, String fromLogicalPath, String toLogicalPath, boolean overwrite) {
        String fromPath = normalizeLogicalPath(fromLogicalPath);
        String toPath = normalizeLogicalPath(toLogicalPath);
        StoredFile source = requireExisting(userId, fromPath);
        String targetParentPath = workspacePathPolicy.extractParentPath(toPath);
        String targetFilename = workspacePathPolicy.extractLeafName(toPath);
        StoredFile target = findExisting(userId, targetParentPath, targetFilename);
        if (target != null && !target.getId().equals(source.getId())) {
            if (!overwrite) {
                throw new BusinessException(ErrorCode.DUPLICATE_NAME, "目标文件已存在");
            }
            workspaceLifecycleApi.recycle(userId, target.getId());
        }
        if (source.getPath().equals(targetParentPath)) {
            WorkspaceMutationResult result = workspaceMutationApi.rename(userId, source.getId(), targetFilename);
            return WorkspaceMoveResult.success(List.of(new WorkspaceMoveItemResult(
                    result.file().id(),
                    result.file().filename(),
                    result.fromPath(),
                    result.toPath(),
                    result.renamed(),
                    false,
                    result.file().customEmoji(),
                    result.file().folderColor()
            )));
        }
        return workspaceMutationApi.move(userId, source.getId(), targetParentPath, null);
    }

    @Override
    @Transactional
    public WorkspaceLifecycleResult recycleByPath(Long userId, String normalizedLogicalPath) {
        StoredFile file = requireExisting(userId, normalizeLogicalPath(normalizedLogicalPath));
        return workspaceLifecycleApi.recycle(userId, file.getId());
    }

    private FileMetadataResponse replaceExistingFile(WebDavWorkspacePutCommand command, StoredFile existing) {
        List<ContentBlobReference> oldBlobsToDelete = contentBlobLifecycleApi.collectBlobReferencesToDelete(
                existing.getBlobId() == null ? List.of() : List.of(existing.getBlobId())
        );
        WorkspaceFileIngressService.ReplacementContent replacement = workspaceFileIngressService.replaceFileContent(
                command.user(),
                existing.getId(),
                command.contentType(),
                command.size(),
                existing.getSize() == null ? 0L : existing.getSize(),
                command.content()
        );
        existing.setBlobId(replacement.blobId());
        existing.setPrimaryEntityId(replacement.primaryEntityId());
        existing.setLegacyStorageName(replacement.objectKey());
        existing.setContentType(command.contentType());
        existing.setSize(command.size());
        StoredFile savedFile = storedFileRepository.save(existing);
        contentBlobLifecycleApi.deleteBlobReferences(oldBlobsToDelete);
        return toResponse(savedFile);
    }

    private StoredFile requireExisting(Long userId, String logicalPath) {
        String parentPath = workspacePathPolicy.extractParentPath(logicalPath);
        String filename = workspacePathPolicy.extractLeafName(logicalPath);
        StoredFile file = findExisting(userId, parentPath, filename);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }
        return file;
    }

    private StoredFile findExisting(Long userId, String parentPath, String filename) {
        return storedFileRepository.findByUserIdAndPathAndFilename(userId, parentPath, filename)
                .orElse(null);
    }

    private String normalizeLogicalPath(String logicalPath) {
        String normalizedPath = workspacePathPolicy.normalizeDirectoryPath(logicalPath);
        if ("/".equals(normalizedPath)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "根目录不能作为目标文件");
        }
        return normalizedPath;
    }

    private FileMetadataResponse toResponse(RegisteredContentFile file) {
        return new FileMetadataResponse(
                file.id(),
                file.filename(),
                file.path(),
                file.size(),
                file.contentType(),
                file.directory(),
                file.createdAt(),
                file.createdAt(),
                null,
                null,
                false
        );
    }

    private FileMetadataResponse toResponse(StoredFile file) {
        LocalDateTime updatedAt = file.getUpdatedAt() == null ? file.getCreatedAt() : file.getUpdatedAt();
        return new FileMetadataResponse(
                file.getId(),
                file.getFilename(),
                file.getPath(),
                file.getSize(),
                file.getContentType(),
                file.isDirectory(),
                file.getCreatedAt(),
                updatedAt,
                file.getCustomEmoji(),
                file.getFolderColor(),
                false
        );
    }
}
