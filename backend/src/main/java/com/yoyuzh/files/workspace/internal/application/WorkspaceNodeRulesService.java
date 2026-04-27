package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

interface RecycleRestoreTargetValidator {

    void validateRecycleRestoreTargets(Long userId,
                                       List<StoredFile> recycleGroupItems,
                                       Function<StoredFile, String> recycleOriginalPathResolver);
}

public final class WorkspaceNodeRulesService {

    private final WorkspacePathPolicy workspacePathPolicy;
    private final RecycleRestoreTargetValidator recycleRestoreTargetValidator;

    public WorkspaceNodeRulesService(WorkspacePathPolicy workspacePathPolicy,
                                     RecycleRestoreTargetValidator recycleRestoreTargetValidator) {
        this.workspacePathPolicy = Objects.requireNonNull(workspacePathPolicy, "workspacePathPolicy");
        this.recycleRestoreTargetValidator = Objects.requireNonNull(recycleRestoreTargetValidator, "recycleRestoreTargetValidator");
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

    public String resolveAvailableNodeName(Long userId, String path, String filename) {
        return workspacePathPolicy.resolveAvailableNodeName(userId, path, filename);
    }

    public boolean existsNodeName(Long userId, String path, String filename) {
        return workspacePathPolicy.existsNodeName(userId, path, filename);
    }

    public void ensureNodeNameAvailable(Long userId, String path, String filename, String errorMessage) {
        workspacePathPolicy.ensureNodeNameAvailable(userId, path, filename, errorMessage);
    }

    public void ensureDirectoryHierarchy(Long userId, String normalizedPath) {
        workspacePathPolicy.ensureDirectoryHierarchy(userId, normalizedPath);
    }

    public void ensureExistingDirectoryPath(Long userId, String normalizedPath) {
        workspacePathPolicy.ensureExistingDirectoryPath(userId, normalizedPath);
    }

    public void validateRecycleRestoreTargets(Long userId,
                                              List<StoredFile> recycleGroupItems,
                                              Function<StoredFile, String> recycleOriginalPathResolver) {
        recycleRestoreTargetValidator.validateRecycleRestoreTargets(userId, recycleGroupItems, recycleOriginalPathResolver);
    }
}
