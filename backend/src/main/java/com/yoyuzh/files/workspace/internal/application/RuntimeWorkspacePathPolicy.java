package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public final class RuntimeWorkspacePathPolicy implements WorkspacePathPolicy, RecycleRestoreTargetValidator {
    private static final Pattern TRAILING_COUNTER_PATTERN = Pattern.compile("^(.*)\\((\\d+)\\)$");
    private static final int AVAILABLE_NAME_RESOLUTION_LIMIT = 100;

    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;

    public RuntimeWorkspacePathPolicy(StoredFileRepository storedFileRepository,
                                      FileContentStorage fileContentStorage) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
    }

    @Override
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
            throw new BusinessException(ErrorCode.INVALID_INPUT, "路径不合法");
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Override
    public String extractParentPath(String normalizedPath) {
        int lastSlash = normalizedPath.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : normalizedPath.substring(0, lastSlash);
    }

    @Override
    public String extractLeafName(String normalizedPath) {
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }

    @Override
    public String buildTargetLogicalPath(String normalizedTargetPath, String filename) {
        return "/".equals(normalizedTargetPath)
                ? "/" + filename
                : normalizedTargetPath + "/" + filename;
    }

    @Override
    public String normalizeUploadFilename(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename);
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "文件名不能为空");
        }
        return normalizeLeafName(filename);
    }

    @Override
    public String normalizeLeafName(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "" : filename).trim();
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "文件名不能为空");
        }
        if (cleaned.contains("/") || cleaned.contains("\\") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "文件名不合法");
        }
        return cleaned;
    }

    @Override
    public String resolveAvailableNodeName(Long userId, String path, String filename) {
        String normalizedFilename = normalizeLeafName(filename);
        if (!existsNodeName(userId, path, normalizedFilename)) {
            return normalizedFilename;
        }
        NameParts nameParts = splitName(normalizedFilename);
        Set<String> existingNames = storedFileRepository.findActiveFilenamesByUserIdAndPathAndFilenamePrefix(
                userId,
                path,
                normalizedFilename,
                escapeLikePrefix(nameParts.baseName()))
                .stream()
                .filter(existingName -> matchesResolvedName(existingName, normalizedFilename, nameParts))
                .collect(Collectors.toSet());
        return resolveAvailableNodeNameFromExistingNames(existingNames, nameParts);
    }

    private String resolveAvailableNodeNameFromExistingNames(Set<String> existingNames, NameParts nameParts) {
        String baseName = nameParts.baseName();
        int counter = nameParts.nextCounter();
        String extension = nameParts.extension();
        for (int attempt = 0; attempt < AVAILABLE_NAME_RESOLUTION_LIMIT; attempt++) {
            String candidate = baseName + "(" + counter + ")" + extension;
            if (!existingNames.contains(candidate)) {
                return candidate;
            }
            counter++;
        }
        throw new BusinessException(ErrorCode.DUPLICATE_NAME, "同名文件过多，无法自动生成可用名称");
    }

    @Override
    public boolean existsNodeName(Long userId, String path, String filename) {
        return storedFileRepository.existsByUserIdAndPathAndFilename(userId, path, filename);
    }

    @Override
    public void ensureNodeNameAvailable(Long userId, String path, String filename, String errorMessage) {
        if (existsNodeName(userId, path, filename)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, errorMessage);
        }
    }

    @Override
    public void ensureDirectoryHierarchy(Long userId, String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return;
        }

        String[] segments = normalizedPath.substring(1).split("/");
        String currentPath = "/";

        for (String segment : segments) {
            Optional<StoredFile> existing = storedFileRepository.findByUserIdAndPathAndFilename(userId, currentPath, segment);
            if (existing.isPresent()) {
                if (!existing.get().isDirectory()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "目标路径不是目录");
                }
                currentPath = "/".equals(currentPath) ? "/" + segment : currentPath + "/" + segment;
                continue;
            }

            String logicalPath = "/".equals(currentPath) ? "/" + segment : currentPath + "/" + segment;
            fileContentStorage.ensureDirectory(userId, logicalPath);

            storedFileRepository.save(StoredFile.directory(userId, currentPath, segment));

            currentPath = logicalPath;
        }
    }

    @Override
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
                throw new BusinessException(ErrorCode.INVALID_INPUT, "目标路径不是目录");
            }
            currentPath = "/".equals(currentPath) ? "/" + segment : currentPath + "/" + segment;
        }
    }

    @Override
    public void validateRecycleRestoreTargets(Long userId,
                                              List<StoredFile> recycleGroupItems,
                                              Function<StoredFile, String> recycleOriginalPathResolver) {
        for (StoredFile item : recycleGroupItems) {
            String originalPath = recycleOriginalPathResolver.apply(item);
            if (existsNodeName(userId, originalPath, item.getFilename())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NAME, "原目录已存在同名文件，无法恢复");
            }
        }
    }

    private NameParts splitName(String filename) {
        int lastDot = filename.lastIndexOf('.');
        String stem = lastDot > 0 ? filename.substring(0, lastDot) : filename;
        String extension = lastDot > 0 ? filename.substring(lastDot) : "";

        Matcher matcher = TRAILING_COUNTER_PATTERN.matcher(stem);
        if (matcher.matches() && StringUtils.hasText(matcher.group(1))) {
            return new NameParts(matcher.group(1), extension, Integer.parseInt(matcher.group(2)) + 1);
        }
        return new NameParts(stem, extension, 1);
    }

    private boolean matchesResolvedName(String candidate, String normalizedFilename, NameParts nameParts) {
        if (normalizedFilename.equals(candidate)) {
            return true;
        }
        if (!candidate.endsWith(nameParts.extension())) {
            return false;
        }
        String stem = nameParts.extension().isEmpty()
                ? candidate
                : candidate.substring(0, candidate.length() - nameParts.extension().length());
        Matcher matcher = TRAILING_COUNTER_PATTERN.matcher(stem);
        return matcher.matches() && nameParts.baseName().equals(matcher.group(1));
    }

    private String escapeLikePrefix(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private record NameParts(String baseName, String extension, int nextCounter) {
    }
}
