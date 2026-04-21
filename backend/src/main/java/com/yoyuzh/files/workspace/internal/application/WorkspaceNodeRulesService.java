package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;

import java.util.List;
import java.util.function.Function;

public final class WorkspaceNodeRulesService {

    private final WorkspacePathPolicy workspacePathPolicy;
    private final RuntimeWorkspacePathPolicy recycleRestorePolicy;

    public WorkspaceNodeRulesService(StoredFileRepository storedFileRepository,
                                     FileContentStorage fileContentStorage) {
        this(new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage));
    }

    public WorkspaceNodeRulesService(RuntimeWorkspacePathPolicy runtimeWorkspacePathPolicy) {
        this.workspacePathPolicy = runtimeWorkspacePathPolicy;
        this.recycleRestorePolicy = runtimeWorkspacePathPolicy;
    }

    public String normalizeDirectoryPath(String path) {
        return workspacePathPolicy.normalizeDirectoryPath(path);
    }

    public String extractParentPath(String normalizedPath) {
        return workspacePathPolicy.extractParentPath(normalizedPath);
    }

    public String extractLeafName(String normalizedPath) {
        return workspacePathPolicy.extractLeafName(normalizedPath);
    }

    public String buildTargetLogicalPath(String normalizedTargetPath, String filename) {
        return workspacePathPolicy.buildTargetLogicalPath(normalizedTargetPath, filename);
    }

    public String normalizeUploadFilename(String originalFilename) {
        return workspacePathPolicy.normalizeUploadFilename(originalFilename);
    }

    public String normalizeLeafName(String filename) {
        return workspacePathPolicy.normalizeLeafName(filename);
    }

    public boolean existsNodeName(Long userId, String path, String filename) {
        return workspacePathPolicy.existsNodeName(userId, path, filename);
    }

    public void ensureNodeNameAvailable(Long userId, String path, String filename, String errorMessage) {
        workspacePathPolicy.ensureNodeNameAvailable(userId, path, filename, errorMessage);
    }

    public void ensureDirectoryHierarchy(User user, String normalizedPath) {
        workspacePathPolicy.ensureDirectoryHierarchy(user.getId(), normalizedPath);
    }

    public void ensureExistingDirectoryPath(Long userId, String normalizedPath) {
        workspacePathPolicy.ensureExistingDirectoryPath(userId, normalizedPath);
    }

    public void validateRecycleRestoreTargets(Long userId,
                                              List<StoredFile> recycleGroupItems,
                                              Function<StoredFile, String> recycleOriginalPathResolver) {
        recycleRestorePolicy.validateRecycleRestoreTargets(userId, recycleGroupItems, recycleOriginalPathResolver);
    }
}
