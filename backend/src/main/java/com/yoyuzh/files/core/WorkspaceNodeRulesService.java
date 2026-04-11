package com.yoyuzh.files.core;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.storage.FileContentStorage;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class WorkspaceNodeRulesService {

    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;

    public WorkspaceNodeRulesService(StoredFileRepository storedFileRepository,
                                     FileContentStorage fileContentStorage) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
    }

    public String normalizeDirectoryPath(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path.trim())) {
            return "/";
        }
        String normalized = path.replace("\\", "/").trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = normalized.replaceAll("/{2,}", "/");
        if (normalized.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "路径不合法");
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String extractParentPath(String normalizedPath) {
        int lastSlash = normalizedPath.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : normalizedPath.substring(0, lastSlash);
    }

    public String extractLeafName(String normalizedPath) {
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }

    public String buildTargetLogicalPath(String normalizedTargetPath, String filename) {
        return "/".equals(normalizedTargetPath)
                ? "/" + filename
                : normalizedTargetPath + "/" + filename;
    }

    public String normalizeUploadFilename(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename);
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不能为空");
        }
        return normalizeLeafName(filename);
    }

    public String normalizeLeafName(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "" : filename).trim();
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不能为空");
        }
        if (cleaned.contains("/") || cleaned.contains("\\") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不合法");
        }
        return cleaned;
    }

    public boolean existsNodeName(Long userId, String path, String filename) {
        return storedFileRepository.existsByUserIdAndPathAndFilename(userId, path, filename);
    }

    public void ensureNodeNameAvailable(Long userId, String path, String filename, String errorMessage) {
        if (existsNodeName(userId, path, filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, errorMessage);
        }
    }

    public void ensureDirectoryHierarchy(User user, String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return;
        }

        String[] segments = normalizedPath.substring(1).split("/");
        String currentPath = "/";

        for (String segment : segments) {
            Optional<StoredFile> existing = storedFileRepository.findByUserIdAndPathAndFilename(user.getId(), currentPath, segment);
            if (existing.isPresent()) {
                if (!existing.get().isDirectory()) {
                    throw new BusinessException(ErrorCode.UNKNOWN, "目标路径不是目录");
                }
                currentPath = "/".equals(currentPath) ? "/" + segment : currentPath + "/" + segment;
                continue;
            }

            String logicalPath = "/".equals(currentPath) ? "/" + segment : currentPath + "/" + segment;
            fileContentStorage.ensureDirectory(user.getId(), logicalPath);

            StoredFile storedFile = new StoredFile();
            storedFile.setUser(user);
            storedFile.setFilename(segment);
            storedFile.setPath(currentPath);
            storedFile.setContentType("directory");
            storedFile.setSize(0L);
            storedFile.setDirectory(true);
            storedFileRepository.save(storedFile);

            currentPath = logicalPath;
        }
    }

    public void ensureExistingDirectoryPath(Long userId, String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return;
        }

        String[] segments = normalizedPath.substring(1).split("/");
        String currentPath = "/";
        for (String segment : segments) {
            StoredFile directory = storedFileRepository.findByUserIdAndPathAndFilename(userId, currentPath, segment)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "目标目录不存在"));
            if (!directory.isDirectory()) {
                throw new BusinessException(ErrorCode.UNKNOWN, "目标路径不是目录");
            }
            currentPath = "/".equals(currentPath) ? "/" + segment : currentPath + "/" + segment;
        }
    }

    public void validateRecycleRestoreTargets(Long userId,
                                              List<StoredFile> recycleGroupItems,
                                              Function<StoredFile, String> recycleOriginalPathResolver) {
        for (StoredFile item : recycleGroupItems) {
            String originalPath = recycleOriginalPathResolver.apply(item);
            if (existsNodeName(userId, originalPath, item.getFilename())) {
                throw new BusinessException(ErrorCode.UNKNOWN, "原目录已存在同名文件，无法恢复");
            }
        }
    }
}
