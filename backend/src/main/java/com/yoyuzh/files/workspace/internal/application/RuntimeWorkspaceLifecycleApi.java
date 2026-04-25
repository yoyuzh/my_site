package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.domain.WorkspaceRecycleLifecycle;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleApi;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleResult;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.files.workspace.api.WorkspaceQuotaGuard;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RuntimeWorkspaceLifecycleApi implements WorkspaceLifecycleApi {

    private final StoredFileRepository storedFileRepository;
    private final ContentDuplicationApi contentDuplicationApi;
    private final ContentBlobQueryApi contentBlobQueryApi;
    private final WorkspacePathPolicy workspacePathPolicy;
    private final WorkspaceNodeRulesService workspaceNodeRulesService;
    private final WorkspaceRecycleLifecycle workspaceRecycleLifecycle;
    private final Clock clock;

    public RuntimeWorkspaceLifecycleApi(StoredFileRepository storedFileRepository,
                                        FileContentStorage fileContentStorage,
                                        ContentDuplicationApi contentDuplicationApi) {
        this(
                storedFileRepository,
                fileContentStorage,
                contentDuplicationApi,
                blobId -> java.util.Optional.empty()
        );
    }

    public RuntimeWorkspaceLifecycleApi(StoredFileRepository storedFileRepository,
                                        ContentDuplicationApi contentDuplicationApi,
                                        ContentBlobQueryApi contentBlobQueryApi,
                                        WorkspacePathPolicy workspacePathPolicy,
                                        WorkspaceNodeRulesService workspaceNodeRulesService) {
        this(
                storedFileRepository,
                contentDuplicationApi,
                contentBlobQueryApi,
                workspacePathPolicy,
                workspaceNodeRulesService,
                new WorkspaceRecycleLifecycle(),
                Clock.systemDefaultZone()
        );
    }

    RuntimeWorkspaceLifecycleApi(StoredFileRepository storedFileRepository,
                                 ContentDuplicationApi contentDuplicationApi,
                                 ContentBlobQueryApi contentBlobQueryApi,
                                 WorkspacePathPolicy workspacePathPolicy,
                                 WorkspaceNodeRulesService workspaceNodeRulesService,
                                 WorkspaceRecycleLifecycle workspaceRecycleLifecycle,
                                 Clock clock) {
        this.storedFileRepository = storedFileRepository;
        this.contentDuplicationApi = contentDuplicationApi;
        this.contentBlobQueryApi = contentBlobQueryApi;
        this.workspacePathPolicy = workspacePathPolicy;
        this.workspaceNodeRulesService = workspaceNodeRulesService;
        this.workspaceRecycleLifecycle = workspaceRecycleLifecycle;
        this.clock = clock;
    }

    public RuntimeWorkspaceLifecycleApi(StoredFileRepository storedFileRepository,
                                        FileContentStorage fileContentStorage,
                                        ContentDuplicationApi contentDuplicationApi,
                                        ContentBlobQueryApi contentBlobQueryApi) {
        RuntimeWorkspacePathPolicy workspacePathPolicy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        this.storedFileRepository = storedFileRepository;
        this.contentDuplicationApi = contentDuplicationApi;
        this.contentBlobQueryApi = contentBlobQueryApi;
        this.workspacePathPolicy = workspacePathPolicy;
        this.workspaceNodeRulesService = new WorkspaceNodeRulesService(workspacePathPolicy, workspacePathPolicy);
        this.workspaceRecycleLifecycle = new WorkspaceRecycleLifecycle();
        this.clock = Clock.systemDefaultZone();
    }

    @Override
    public WorkspaceLifecycleResult copy(Long userId, Long fileId, String normalizedTargetPath, WorkspaceQuotaGuard quotaGuard) {
        StoredFile storedFile = getOwnedActiveFile(userId, fileId, "复制");
        workspacePathPolicy.ensureExistingDirectoryPath(userId, normalizedTargetPath);
        workspacePathPolicy.ensureNodeNameAvailable(userId, normalizedTargetPath, storedFile.getFilename(), "目标目录已存在同名文件");

        if (!storedFile.isDirectory()) {
            quotaGuard.ensureWithinQuota(storedFile.getSize());
            RegisteredContentFile savedFile = duplicateBlobBackedFile(storedFile.copyForOwner(userId, normalizedTargetPath), userId);
            return new WorkspaceLifecycleResult(
                    toResponse(savedFile),
                    buildLogicalPath(storedFile),
                    buildLogicalPath(savedFile.path(), savedFile.filename()),
                    List.of(normalizedTargetPath)
            );
        }

        String oldLogicalPath = buildLogicalPath(storedFile);
        String newLogicalPath = workspacePathPolicy.buildTargetLogicalPath(normalizedTargetPath, storedFile.getFilename());
        if (newLogicalPath.equals(oldLogicalPath) || newLogicalPath.startsWith(oldLogicalPath + "/")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "不能复制到当前目录或其子目录");
        }

        List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(userId, oldLogicalPath);
        long additionalBytes = descendants.stream()
                .filter(descendant -> !descendant.isDirectory())
                .mapToLong(StoredFile::getSize)
                .sum();
        quotaGuard.ensureWithinQuota(additionalBytes);
        List<StoredFile> copiedEntries = new ArrayList<>();

        StoredFile copiedRoot = storedFile.copyForOwner(userId, normalizedTargetPath);
        copiedEntries.add(copiedRoot);

        descendants.stream()
                .sorted(Comparator
                        .comparingInt((StoredFile descendant) -> descendant.getPath().length())
                        .thenComparing(descendant -> descendant.isDirectory() ? 0 : 1)
                        .thenComparing(StoredFile::getFilename))
                .forEach(descendant -> {
                    String copiedPath = remapCopiedPath(descendant.getPath(), oldLogicalPath, newLogicalPath);
                    workspacePathPolicy.ensureNodeNameAvailable(userId, copiedPath, descendant.getFilename(), "目标目录已存在同名文件");
                    copiedEntries.add(descendant.copyForOwner(userId, copiedPath));
                });

        StoredFile savedRoot = null;
        for (StoredFile copiedEntry : copiedEntries) {
            StoredFile savedEntry = copiedEntry.isDirectory()
                    ? storedFileRepository.save(copiedEntry)
                    : toStoredFile(duplicateBlobBackedFile(copiedEntry, userId));
            if (savedRoot == null) {
                savedRoot = savedEntry;
            }
        }

        StoredFile root = savedRoot == null ? copiedRoot : savedRoot;
        return new WorkspaceLifecycleResult(
                toResponse(root),
                oldLogicalPath,
                newLogicalPath,
                List.of(normalizedTargetPath)
        );
    }

    @Override
    public WorkspaceLifecycleResult recycle(Long userId, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(userId, fileId, "删除");
        String fromPath = buildLogicalPath(storedFile);
        List<StoredFile> filesToRecycle = new ArrayList<>();
        filesToRecycle.add(storedFile);
        if (storedFile.isDirectory()) {
            String logicalPath = buildLogicalPath(storedFile);
            filesToRecycle.addAll(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(userId, logicalPath));
        }
        workspaceRecycleLifecycle.moveToRecycleBin(filesToRecycle, storedFile.getId(), now());
        storedFileRepository.saveAll(filesToRecycle);
        return new WorkspaceLifecycleResult(
                toResponse(storedFile),
                fromPath,
                buildLogicalPath(storedFile),
                List.of(workspacePathPolicy.extractParentPath(fromPath))
        );
    }

    @Override
    public WorkspaceLifecycleResult restore(Long userId, Long fileId, WorkspaceQuotaGuard quotaGuard) {
        StoredFile recycleRoot = getOwnedRecycleRootFile(userId, fileId);
        String fromPath = buildLogicalPath(recycleRoot);
        String restoreParentPath = requireRecycleOriginalPath(recycleRoot);
        String toPath = workspacePathPolicy.buildTargetLogicalPath(restoreParentPath, recycleRoot.getFilename());
        List<StoredFile> recycleGroupItems = loadRecycleGroupItems(recycleRoot);
        long additionalBytes = recycleGroupItems.stream()
                .filter(item -> !item.isDirectory())
                .mapToLong(StoredFile::getSize)
                .sum();
        quotaGuard.ensureWithinQuota(additionalBytes);
        workspaceNodeRulesService.validateRecycleRestoreTargets(userId, recycleGroupItems, this::requireRecycleOriginalPath);
        workspacePathPolicy.ensureDirectoryHierarchy(userId, requireRecycleOriginalPath(recycleRoot));

        workspaceRecycleLifecycle.restoreFromRecycleBin(recycleGroupItems);
        storedFileRepository.saveAll(recycleGroupItems);
        return new WorkspaceLifecycleResult(
                toResponse(recycleRoot),
                fromPath,
                toPath,
                List.of(restoreParentPath)
        );
    }

    private StoredFile getOwnedActiveFile(Long userId, Long fileId, String action) {
        StoredFile storedFile = storedFileRepository.findDetailedById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!userId.equals(storedFile.getUserId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限" + action + "该文件");
        }
        if (storedFile.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }
        return storedFile;
    }

    private StoredFile getOwnedRecycleRootFile(Long userId, Long fileId) {
        StoredFile storedFile = storedFileRepository.findDetailedById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!userId.equals(storedFile.getUserId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限恢复该文件");
        }
        if (storedFile.getDeletedAt() == null || !storedFile.isRecycleRoot()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return storedFile;
    }

    private List<StoredFile> loadRecycleGroupItems(StoredFile recycleRoot) {
        List<StoredFile> items = storedFileRepository.findByRecycleGroupId(recycleRoot.getRecycleGroupId());
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return items;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private String requireRecycleOriginalPath(StoredFile storedFile) {
        if (!StringUtils.hasText(storedFile.getRecycleOriginalPath())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return storedFile.getRecycleOriginalPath();
    }

    private String remapCopiedPath(String currentPath, String oldLogicalPath, String newLogicalPath) {
        if (currentPath.equals(oldLogicalPath)) {
            return newLogicalPath;
        }
        return newLogicalPath + currentPath.substring(oldLogicalPath.length());
    }

    private RegisteredContentFile duplicateBlobBackedFile(StoredFile copiedFile, Long ownerUserId) {
        ContentBlobReference blob = requireBlobReference(copiedFile);
        return contentDuplicationApi.duplicateBlobBackedFile(
                new ContentRegistrationCommand(
                        ownerUserId,
                        copiedFile.getPath(),
                        copiedFile.getFilename(),
                        copiedFile.getContentType(),
                        copiedFile.getSize(),
                        blob
                )
        );
    }

    private ContentBlobReference requireBlobReference(StoredFile storedFile) {
        if (storedFile.getBlobId() == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件内容不存在");
        }
        return contentBlobQueryApi.findBlobReferenceById(storedFile.getBlobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件内容不存在"));
    }

    private StoredFile toStoredFile(RegisteredContentFile savedFile) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(savedFile.id());
        storedFile.renameTo(savedFile.filename());
        storedFile.moveTo(savedFile.path());
        storedFile.setSize(savedFile.size());
        storedFile.setContentType(savedFile.contentType());
        storedFile.setDirectory(savedFile.directory());
        storedFile.setCreatedAt(savedFile.createdAt());
        return storedFile;
    }

    private FileMetadataResponse toResponse(RegisteredContentFile storedFile) {
        return new FileMetadataResponse(
                storedFile.id(),
                storedFile.filename(),
                storedFile.path(),
                storedFile.size(),
                storedFile.contentType(),
                storedFile.directory(),
                storedFile.createdAt(),
                storedFile.createdAt(),
                false
        );
    }

    private FileMetadataResponse toResponse(StoredFile storedFile) {
        String logicalPath = storedFile.isDirectory() ? buildLogicalPath(storedFile) : storedFile.getPath();
        return new FileMetadataResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                logicalPath,
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt(),
                storedFile.getUpdatedAt() != null ? storedFile.getUpdatedAt() : storedFile.getCreatedAt(),
                false
        );
    }

    private String buildLogicalPath(StoredFile storedFile) {
        return storedFile.logicalPath();
    }

    private String buildLogicalPath(String path, String filename) {
        return "/".equals(path)
                ? "/" + filename
                : path + "/" + filename;
    }
}
