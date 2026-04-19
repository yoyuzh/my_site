package com.yoyuzh.files.workspace.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.core.StoredFile;

import java.util.List;
import java.util.function.Function;

public interface WorkspacePathPolicy {

    String normalizeDirectoryPath(String path);

    String extractParentPath(String normalizedPath);

    String extractLeafName(String normalizedPath);

    String buildTargetLogicalPath(String normalizedTargetPath, String filename);

    String normalizeUploadFilename(String originalFilename);

    String normalizeLeafName(String filename);

    boolean existsNodeName(Long userId, String path, String filename);

    void ensureNodeNameAvailable(Long userId, String path, String filename, String errorMessage);

    void ensureDirectoryHierarchy(User user, String normalizedPath);

    void ensureExistingDirectoryPath(Long userId, String normalizedPath);

    void validateRecycleRestoreTargets(Long userId,
                                       List<StoredFile> recycleGroupItems,
                                       Function<StoredFile, String> recycleOriginalPathResolver);
}
