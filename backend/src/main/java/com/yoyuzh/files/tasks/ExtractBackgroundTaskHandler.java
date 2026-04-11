package com.yoyuzh.files.tasks;

import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional
public class ExtractBackgroundTaskHandler implements BackgroundTaskHandler {

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final BackgroundTaskStateManager stateManager;

    public ExtractBackgroundTaskHandler(StoredFileRepository storedFileRepository,
                                        UserRepository userRepository,
                                        FileService fileService,
                                        BackgroundTaskStateManager stateManager) {
        this.storedFileRepository = storedFileRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.stateManager = stateManager;
    }

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.EXTRACT;
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task) {
        return handle(task, publicStatePatch -> {
        });
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        Map<String, Object> state = stateManager.mergeJsonObjects(
                task.getPublicStateJson(),
                task.getPrivateStateJson(),
                "extract task state is invalid"
        );
        Long fileId = stateManager.readLong(state.get("fileId"));
        String outputPath = stateManager.readText(state.get("outputPath"));
        String outputDirectoryName = stateManager.readText(state.get("outputDirectoryName"));
        if (fileId == null) {
            throw new IllegalStateException("extract task missing fileId");
        }
        if (!StringUtils.hasText(outputPath) || !StringUtils.hasText(outputDirectoryName)) {
            throw new IllegalStateException("extract task missing output target");
        }

        StoredFile archive = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, task.getUserId())
                .orElseThrow(() -> new IllegalStateException("extract task file not found"));
        if (archive.isDirectory()) {
            throw new IllegalStateException("extract task only supports files");
        }
        User user = userRepository.findById(task.getUserId())
                .orElseThrow(() -> new IllegalStateException("extract task user not found"));

        ExtractPlan plan = parseArchivePlan(archive, outputPath, outputDirectoryName);
        progressReporter.report(progressPatch(0, plan.files().size(), 0, plan.directories().size()));
        executePlan(user, plan, progressReporter);

        Map<String, Object> publicStatePatch = new LinkedHashMap<>();
        publicStatePatch.put("worker", "extract");
        publicStatePatch.put("extractedPath", plan.extractedPath());
        publicStatePatch.put("extractedFileCount", plan.files().size());
        publicStatePatch.put("extractedDirectoryCount", plan.directories().size());
        publicStatePatch.putAll(progressPatch(
                plan.files().size(),
                plan.files().size(),
                plan.directories().size(),
                plan.directories().size()
        ));
        return new BackgroundTaskHandlerResult(publicStatePatch);
    }

    private void executePlan(User user, ExtractPlan plan, BackgroundTaskProgressReporter progressReporter) {
        fileService.importExternalFilesAtomically(
                user,
                plan.directories(),
                plan.files().stream()
                        .map(file -> new FileService.ExternalFileImport(
                                file.parentPath(),
                                file.filename(),
                                file.contentType(),
                                file.content()
                        ))
                        .toList(),
                progress -> progressReporter.report(progressPatch(
                        progress.processedFileCount(),
                        progress.totalFileCount(),
                        progress.processedDirectoryCount(),
                        progress.totalDirectoryCount()
                ))
        );
    }

    private Map<String, Object> progressPatch(int processedFileCount,
                                              int totalFileCount,
                                              int processedDirectoryCount,
                                              int totalDirectoryCount) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("processedFileCount", processedFileCount);
        patch.put("totalFileCount", totalFileCount);
        patch.put("processedDirectoryCount", processedDirectoryCount);
        patch.put("totalDirectoryCount", totalDirectoryCount);
        patch.put("progressPercent", calculateProgressPercent(
                processedFileCount,
                totalFileCount,
                processedDirectoryCount,
                totalDirectoryCount
        ));
        return patch;
    }

    private int calculateProgressPercent(int processedFileCount,
                                         int totalFileCount,
                                         int processedDirectoryCount,
                                         int totalDirectoryCount) {
        int total = Math.max(0, totalFileCount) + Math.max(0, totalDirectoryCount);
        int processed = Math.max(0, processedFileCount) + Math.max(0, processedDirectoryCount);
        if (total <= 0) {
            return 100;
        }
        return Math.min(100, (int) Math.floor((processed * 100.0d) / total));
    }

    private ExtractPlan parseArchivePlan(StoredFile archive, String outputPath, String outputDirectoryName) {
        FileService.ZipCompatibleArchive zipArchive;
        try {
            zipArchive = fileService.readZipCompatibleArchive(archive);
        } catch (BusinessException ex) {
            throw new IllegalStateException("extract task only supports zip-compatible archives", ex);
        }

        List<ZipItem> items = zipArchive.entries().stream()
                .map(entry -> toZipItem(entry, zipArchive.commonRootDirectoryName()))
                .filter(item -> StringUtils.hasText(item.path()))
                .toList();
        if (items.isEmpty()) {
            throw new IllegalStateException("extract task archive is empty");
        }

        String normalizedOutputPath = normalizeDirectoryPath(outputPath);
        if (shouldExtractSingleFileToParent(items, outputDirectoryName)) {
            ZipItem fileItem = items.get(0);
            return new ExtractPlan(
                    List.of(),
                    List.of(new ExtractedFile(
                            normalizedOutputPath,
                            outputDirectoryName,
                            fileItem.contentType(),
                            fileItem.content()
                    )),
                    normalizedOutputPath
            );
        }

        String rootPath = joinPath(normalizedOutputPath, outputDirectoryName);
        LinkedHashSet<String> directories = new LinkedHashSet<>();
        directories.add(rootPath);
        List<ExtractedFile> files = new ArrayList<>();
        for (ZipItem item : items) {
            if (item.directory()) {
                directories.add(joinPath(rootPath, trimTrailingSlash(item.path())));
                continue;
            }
            String relativeParent = extractParentPath(item.path());
            String targetParent = StringUtils.hasText(relativeParent) ? joinPath(rootPath, relativeParent) : rootPath;
            collectParentDirectories(directories, rootPath, relativeParent);
            files.add(new ExtractedFile(
                    targetParent,
                    extractLeafName(item.path()),
                    item.contentType(),
                    item.content()
            ));
        }

        return new ExtractPlan(List.copyOf(directories), List.copyOf(files), rootPath);
    }

    private void collectParentDirectories(LinkedHashSet<String> directories, String rootPath, String relativeParent) {
        if (!StringUtils.hasText(relativeParent)) {
            return;
        }
        String current = "";
        for (String segment : relativeParent.split("/")) {
            current = StringUtils.hasText(current) ? current + "/" + segment : segment;
            directories.add(joinPath(rootPath, current));
        }
    }

    private boolean shouldExtractSingleFileToParent(List<ZipItem> items, String outputDirectoryName) {
        if (items.size() != 1) {
            return false;
        }
        ZipItem item = items.get(0);
        return !item.directory()
                && !item.path().contains("/")
                && outputDirectoryName.equals(item.path());
    }

    private ZipItem toZipItem(FileService.ZipCompatibleArchiveEntry entry, String commonRootDirectoryName) {
        String path = stripCommonRootDirectory(entry.relativePath(), commonRootDirectoryName);
        return new ZipItem(path, entry.directory(), entry.content(), guessContentType(path));
    }

    private String stripCommonRootDirectory(String relativePath, String commonRootDirectoryName) {
        if (!StringUtils.hasText(relativePath) || !StringUtils.hasText(commonRootDirectoryName)) {
            return relativePath;
        }
        String prefix = commonRootDirectoryName + "/";
        if (relativePath.equals(commonRootDirectoryName)) {
            return "";
        }
        if (relativePath.startsWith(prefix)) {
            return relativePath.substring(prefix.length());
        }
        return relativePath;
    }

    private String normalizeDirectoryPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String joinPath(String parent, String leaf) {
        String normalizedParent = normalizeDirectoryPath(parent);
        String normalizedLeaf = trimSlashes(leaf);
        if (!StringUtils.hasText(normalizedLeaf)) {
            return normalizedParent;
        }
        if ("/".equals(normalizedParent)) {
            return "/" + normalizedLeaf;
        }
        return normalizedParent + "/" + normalizedLeaf;
    }

    private String trimSlashes(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String extractParentPath(String path) {
        int separator = path.lastIndexOf('/');
        if (separator < 0) {
            return "";
        }
        return path.substring(0, separator);
    }

    private String extractLeafName(String path) {
        int separator = path.lastIndexOf('/');
        if (separator < 0) {
            return path;
        }
        return path.substring(separator + 1);
    }

    private String guessContentType(String entryPath) {
        String contentType = URLConnection.guessContentTypeFromName(entryPath);
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return "application/octet-stream";
    }

    private record ZipItem(String path, boolean directory, byte[] content, String contentType) {
    }

    private record ExtractedFile(String parentPath, String filename, String contentType, byte[] content) {
    }

    private record ExtractPlan(List<String> directories, List<ExtractedFile> files, String extractedPath) {
    }
}
