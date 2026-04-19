package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleApi;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleResult;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.files.workspace.api.WorkspaceQuotaGuard;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class RuntimeWorkspaceLifecycleApi implements WorkspaceLifecycleApi {

    private static final String RECYCLE_BIN_PATH_PREFIX = "/.recycle";

    private final StoredFileRepository storedFileRepository;
    private final ContentDuplicationApi contentDuplicationApi;
    private final WorkspacePathPolicy workspacePathPolicy;

    public RuntimeWorkspaceLifecycleApi(StoredFileRepository storedFileRepository,
                                        FileContentStorage fileContentStorage,
                                        ContentDuplicationApi contentDuplicationApi) {
        this.storedFileRepository = storedFileRepository;
        this.contentDuplicationApi = contentDuplicationApi;
        this.workspacePathPolicy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
    }

    @Override
    public WorkspaceLifecycleResult copy(User user, Long fileId, String normalizedTargetPath, WorkspaceQuotaGuard quotaGuard) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "复制");
        workspacePathPolicy.ensureExistingDirectoryPath(user.getId(), normalizedTargetPath);
        workspacePathPolicy.ensureNodeNameAvailable(user.getId(), normalizedTargetPath, storedFile.getFilename(), "目标目录已存在同名文件");

        if (!storedFile.isDirectory()) {
            quotaGuard.ensureWithinQuota(storedFile.getSize());
            RegisteredContentFile savedFile = duplicateBlobBackedFile(copyStoredFile(storedFile, user, normalizedTargetPath), user);
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

        List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), oldLogicalPath);
        long additionalBytes = descendants.stream()
                .filter(descendant -> !descendant.isDirectory())
                .mapToLong(StoredFile::getSize)
                .sum();
        quotaGuard.ensureWithinQuota(additionalBytes);
        List<StoredFile> copiedEntries = new ArrayList<>();

        StoredFile copiedRoot = copyStoredFile(storedFile, user, normalizedTargetPath);
        copiedEntries.add(copiedRoot);

        descendants.stream()
                .sorted(Comparator
                        .comparingInt((StoredFile descendant) -> descendant.getPath().length())
                        .thenComparing(descendant -> descendant.isDirectory() ? 0 : 1)
                        .thenComparing(StoredFile::getFilename))
                .forEach(descendant -> {
                    String copiedPath = remapCopiedPath(descendant.getPath(), oldLogicalPath, newLogicalPath);
                    workspacePathPolicy.ensureNodeNameAvailable(user.getId(), copiedPath, descendant.getFilename(), "目标目录已存在同名文件");
                    copiedEntries.add(copyStoredFile(descendant, user, copiedPath));
                });

        StoredFile savedRoot = null;
        for (StoredFile copiedEntry : copiedEntries) {
            StoredFile savedEntry = copiedEntry.isDirectory()
                    ? storedFileRepository.save(copiedEntry)
                    : toStoredFile(duplicateBlobBackedFile(copiedEntry, user));
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
    public WorkspaceLifecycleResult recycle(User user, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "删除");
        String fromPath = buildLogicalPath(storedFile);
        List<StoredFile> filesToRecycle = new ArrayList<>();
        filesToRecycle.add(storedFile);
        if (storedFile.isDirectory()) {
            String logicalPath = buildLogicalPath(storedFile);
            filesToRecycle.addAll(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), logicalPath));
        }
        moveToRecycleBin(filesToRecycle, storedFile.getId());
        return new WorkspaceLifecycleResult(
                toResponse(storedFile),
                fromPath,
                buildLogicalPath(storedFile),
                List.of(workspacePathPolicy.extractParentPath(fromPath))
        );
    }

    @Override
    public WorkspaceLifecycleResult restore(User user, Long fileId, WorkspaceQuotaGuard quotaGuard) {
        StoredFile recycleRoot = getOwnedRecycleRootFile(user, fileId);
        String fromPath = buildLogicalPath(recycleRoot);
        String restoreParentPath = requireRecycleOriginalPath(recycleRoot);
        String toPath = workspacePathPolicy.buildTargetLogicalPath(restoreParentPath, recycleRoot.getFilename());
        List<StoredFile> recycleGroupItems = loadRecycleGroupItems(recycleRoot);
        long additionalBytes = recycleGroupItems.stream()
                .filter(item -> !item.isDirectory())
                .mapToLong(StoredFile::getSize)
                .sum();
        quotaGuard.ensureWithinQuota(additionalBytes);
        workspacePathPolicy.validateRecycleRestoreTargets(user.getId(), recycleGroupItems, this::requireRecycleOriginalPath);
        workspacePathPolicy.ensureDirectoryHierarchy(user, requireRecycleOriginalPath(recycleRoot));

        for (StoredFile item : recycleGroupItems) {
            item.setPath(requireRecycleOriginalPath(item));
            item.setDeletedAt(null);
            item.setRecycleOriginalPath(null);
            item.setRecycleGroupId(null);
            item.setRecycleRoot(false);
        }
        storedFileRepository.saveAll(recycleGroupItems);
        return new WorkspaceLifecycleResult(
                toResponse(recycleRoot),
                fromPath,
                toPath,
                List.of(restoreParentPath)
        );
    }

    private StoredFile getOwnedActiveFile(User user, Long fileId, String action) {
        StoredFile storedFile = storedFileRepository.findDetailedById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!storedFile.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限" + action + "该文件");
        }
        if (storedFile.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }
        return storedFile;
    }

    private StoredFile getOwnedRecycleRootFile(User user, Long fileId) {
        StoredFile storedFile = storedFileRepository.findDetailedById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!storedFile.getUser().getId().equals(user.getId())) {
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

    private void moveToRecycleBin(List<StoredFile> filesToRecycle, Long recycleRootId) {
        if (filesToRecycle.isEmpty()) {
            return;
        }

        StoredFile recycleRoot = filesToRecycle.stream()
                .filter(item -> recycleRootId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        String recycleGroupId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime deletedAt = LocalDateTime.now();
        String rootLogicalPath = buildLogicalPath(recycleRoot);
        String recycleRootPath = buildRecycleBinPath(recycleGroupId, recycleRoot.getPath());
        String recycleRootLogicalPath = workspacePathPolicy.buildTargetLogicalPath(recycleRootPath, recycleRoot.getFilename());

        List<StoredFile> orderedItems = filesToRecycle.stream()
                .sorted(Comparator
                        .comparingInt((StoredFile item) -> buildLogicalPath(item).length())
                        .thenComparing(item -> item.isDirectory() ? 0 : 1)
                        .thenComparing(StoredFile::getFilename))
                .toList();

        for (StoredFile item : orderedItems) {
            String originalPath = item.getPath();
            String recyclePath = recycleRootId.equals(item.getId())
                    ? recycleRootPath
                    : remapCopiedPath(item.getPath(), rootLogicalPath, recycleRootLogicalPath);
            item.setDeletedAt(deletedAt);
            item.setRecycleOriginalPath(originalPath);
            item.setRecycleGroupId(recycleGroupId);
            item.setRecycleRoot(recycleRootId.equals(item.getId()));
            item.setPath(recyclePath);
        }

        storedFileRepository.saveAll(orderedItems);
    }

    private String buildRecycleBinPath(String recycleGroupId, String originalPath) {
        if ("/".equals(originalPath)) {
            return RECYCLE_BIN_PATH_PREFIX + "/" + recycleGroupId;
        }
        return RECYCLE_BIN_PATH_PREFIX + "/" + recycleGroupId + originalPath;
    }

    private String requireRecycleOriginalPath(StoredFile storedFile) {
        if (!StringUtils.hasText(storedFile.getRecycleOriginalPath())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return storedFile.getRecycleOriginalPath();
    }

    private StoredFile copyStoredFile(StoredFile source, User owner, String nextPath) {
        StoredFile copiedFile = new StoredFile();
        copiedFile.setUser(owner);
        copiedFile.setFilename(source.getFilename());
        copiedFile.setPath(nextPath);
        copiedFile.setContentType(source.getContentType());
        copiedFile.setSize(source.getSize());
        copiedFile.setDirectory(source.isDirectory());
        copiedFile.setBlob(source.getBlob());
        return copiedFile;
    }

    private String remapCopiedPath(String currentPath, String oldLogicalPath, String newLogicalPath) {
        if (currentPath.equals(oldLogicalPath)) {
            return newLogicalPath;
        }
        return newLogicalPath + currentPath.substring(oldLogicalPath.length());
    }

    private RegisteredContentFile duplicateBlobBackedFile(StoredFile copiedFile, User owner) {
        return contentDuplicationApi.duplicateBlobBackedFile(
                new ContentRegistrationCommand(
                        owner,
                        copiedFile.getPath(),
                        copiedFile.getFilename(),
                        copiedFile.getContentType(),
                        copiedFile.getSize(),
                        copiedFile.getBlob()
                )
        );
    }

    private StoredFile toStoredFile(RegisteredContentFile savedFile) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(savedFile.id());
        storedFile.setFilename(savedFile.filename());
        storedFile.setPath(savedFile.path());
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
                storedFile.createdAt()
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
                storedFile.getCreatedAt()
        );
    }

    private String buildLogicalPath(StoredFile storedFile) {
        return buildLogicalPath(storedFile.getPath(), storedFile.getFilename());
    }

    private String buildLogicalPath(String path, String filename) {
        return "/".equals(path)
                ? "/" + filename
                : path + "/" + filename;
    }
}
