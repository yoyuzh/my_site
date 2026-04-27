package com.yoyuzh.files.workspace.api;

public interface WorkspacePathPolicy {

    String normalizeDirectoryPath(String path);

    String extractParentPath(String normalizedPath);

    String extractLeafName(String normalizedPath);

    String buildTargetLogicalPath(String normalizedTargetPath, String filename);

    String normalizeUploadFilename(String originalFilename);

    String normalizeLeafName(String filename);

    String resolveAvailableNodeName(Long userId, String path, String filename);

    boolean existsNodeName(Long userId, String path, String filename);

    void ensureNodeNameAvailable(Long userId, String path, String filename, String errorMessage);

    void ensureDirectoryHierarchy(Long userId, String normalizedPath);

    void ensureExistingDirectoryPath(Long userId, String normalizedPath);
}
