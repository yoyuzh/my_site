package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceExternalFileImport;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.transfer.internal.domain.DownloadEngineType;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadCandidateFile;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class RemoteDownloadImportService {

    private final IdentityUserDirectoryApi identityUserDirectoryApi;
    private final WorkspaceBootstrapApi workspaceBootstrapApi;

    public RemoteDownloadImportService(IdentityUserDirectoryApi identityUserDirectoryApi,
                                       WorkspaceBootstrapApi workspaceBootstrapApi) {
        this.identityUserDirectoryApi = identityUserDirectoryApi;
        this.workspaceBootstrapApi = workspaceBootstrapApi;
    }

    public int importCompletedDownload(RemoteDownloadTask task, String outputPath, String savePath) {
        IdentityUserSnapshot user = identityUserDirectoryApi.findSnapshotById(task.getUserId())
                .orElseThrow(() -> new IllegalStateException("remote download user not found"));
        WorkspaceUserContext workspaceUser = workspaceUser(user);
        ImportPlan plan = task.getEngineType() == DownloadEngineType.ARIA2
                ? buildAria2ImportPlan(workspaceUser, task, outputPath)
                : buildQbittorrentImportPlan(workspaceUser, task, outputPath, savePath);
        workspaceBootstrapApi.importExternalFilesAtomically(
                workspaceUser,
                plan.directories(),
                plan.files(),
                null
        );
        cleanupAria2Source(plan.cleanupPath());
        return plan.files().size();
    }

    private ImportPlan buildAria2ImportPlan(WorkspaceUserContext workspaceUser, RemoteDownloadTask task, String outputPath) {
        Path source = requireRegularFile(outputPath, "aria2 output path is invalid");
        String targetPath = normalizeDirectoryPath(task.getTargetPath());
        return new ImportPlan(
                List.of(),
                List.of(new WorkspaceExternalFileImport(
                        targetPath,
                        resolveAvailableFilename(workspaceUser, targetPath, source.getFileName().toString()),
                        guessContentType(source.getFileName().toString()),
                        fileSize(source),
                        () -> Files.newInputStream(source)
                )),
                source
        );
    }

    private ImportPlan buildQbittorrentImportPlan(WorkspaceUserContext workspaceUser,
                                                  RemoteDownloadTask task,
                                                  String outputPath,
                                                  String savePath) {
        List<RemoteDownloadCandidateFile> selectedFiles = task.getCandidateFiles().stream()
                .filter(RemoteDownloadCandidateFile::isSelected)
                .toList();
        if (selectedFiles.isEmpty()) {
            throw new IllegalStateException("remote download has no selected files to import");
        }
        Path sourceRoot = resolveQbittorrentSourceRoot(outputPath, savePath);
        String targetRoot = normalizeDirectoryPath(task.getTargetPath());
        LinkedHashSet<String> directories = new LinkedHashSet<>();
        List<WorkspaceExternalFileImport> files = new ArrayList<>();
        DirectoryNameTracker directoryNameTracker = new DirectoryNameTracker(workspaceUser);
        DuplicateNameTracker fileNameTracker = new DuplicateNameTracker(workspaceUser);
        for (RemoteDownloadCandidateFile candidateFile : selectedFiles) {
            String relativePath = normalizeRelativePath(candidateFile.getRelativePath());
            Path source = sourceRoot.resolve(relativePath).normalize();
            if (!source.startsWith(sourceRoot)) {
                throw new IllegalStateException("remote download source path escaped save root");
            }
            requireRegularFile(source.toString(), "remote download source file is missing");
            String parentRelativePath = extractParentPath(relativePath);
            String targetPath = StringUtils.hasText(parentRelativePath)
                    ? directoryNameTracker.resolvePath(targetRoot, parentRelativePath, directories)
                    : targetRoot;
            if (StringUtils.hasText(parentRelativePath)) {
                directories.add(targetPath);
            }
            files.add(new WorkspaceExternalFileImport(
                    targetPath,
                    fileNameTracker.resolve(targetPath, extractLeafName(relativePath)),
                    guessContentType(relativePath),
                    fileSize(source),
                    () -> Files.newInputStream(source)
            ));
        }
        return new ImportPlan(List.copyOf(directories), List.copyOf(files), null);
    }

    private Path resolveQbittorrentSourceRoot(String outputPath, String savePath) {
        if (StringUtils.hasText(savePath)) {
            Path path = Path.of(savePath).normalize();
            if (!Files.isDirectory(path)) {
                throw new IllegalStateException("qBittorrent save path is invalid");
            }
            return path;
        }
        if (!StringUtils.hasText(outputPath)) {
            throw new IllegalStateException("qBittorrent output path is missing");
        }
        Path output = Path.of(outputPath).normalize();
        if (Files.isDirectory(output)) {
            Path parent = output.getParent();
            return parent == null ? output : parent;
        }
        Path parent = output.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalStateException("qBittorrent output path is invalid");
        }
        return parent;
    }

    private Path requireRegularFile(String pathValue, String message) {
        if (!StringUtils.hasText(pathValue)) {
            throw new IllegalStateException(message);
        }
        Path path = Path.of(pathValue).normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(message);
        }
        return path;
    }

    private long fileSize(Path source) {
        try {
            return Files.size(source);
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to inspect remote download source", ex);
        }
    }

    private void cleanupAria2Source(Path cleanupPath) {
        if (cleanupPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(cleanupPath);
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to cleanup imported aria2 file", ex);
        }
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

    private String normalizeRelativePath(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!StringUtils.hasText(normalized) || normalized.contains("..")) {
            throw new IllegalStateException("remote download relative path is invalid");
        }
        return normalized;
    }

    private String joinPath(String parent, String leaf) {
        String normalizedParent = normalizeDirectoryPath(parent);
        String normalizedLeaf = normalizeRelativePath(leaf);
        if ("/".equals(normalizedParent)) {
            return "/" + normalizedLeaf;
        }
        return normalizedParent + "/" + normalizedLeaf;
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

    private String resolveAvailableFilename(WorkspaceUserContext user, String path, String filename) {
        return resolveAvailableFilename(user, path, filename, new LinkedHashSet<>());
    }

    private String resolveAvailableFilename(WorkspaceUserContext user, String path, String filename, LinkedHashSet<String> reservedNames) {
        if (!reservedNames.contains(filename) && !workspaceBootstrapApi.existsNode(user, path, filename)) {
            return filename;
        }
        NameParts nameParts = splitName(filename);
        for (int counter = 1; counter <= 100; counter += 1) {
            String candidate = nameParts.baseName() + "(" + counter + ")" + nameParts.extension();
            if (!reservedNames.contains(candidate) && !workspaceBootstrapApi.existsNode(user, path, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("remote download target has too many duplicate names");
    }

    private NameParts splitName(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return new NameParts(filename.substring(0, lastDot), filename.substring(lastDot));
        }
        return new NameParts(filename, "");
    }

    private String guessContentType(String filename) {
        String contentType = URLConnection.guessContentTypeFromName(filename);
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private WorkspaceUserContext workspaceUser(IdentityUserSnapshot user) {
        return new WorkspaceUserContext(
                user.id(),
                user.storageQuotaBytes(),
                user.maxUploadSizeBytes()
        );
    }

    private record ImportPlan(
            List<String> directories,
            List<WorkspaceExternalFileImport> files,
            Path cleanupPath
    ) {
    }

    private record NameParts(String baseName, String extension) {
    }

    private final class DuplicateNameTracker {
        private final WorkspaceUserContext user;
        private final Map<String, LinkedHashSet<String>> reservedNamesByPath = new HashMap<>();

        private DuplicateNameTracker(WorkspaceUserContext user) {
            this.user = user;
        }

        private String resolve(String path, String filename) {
            String normalizedPath = normalizeDirectoryPath(path);
            LinkedHashSet<String> reservedNames = reservedNamesByPath.computeIfAbsent(normalizedPath, ignored -> new LinkedHashSet<>());
            String resolved = resolveAvailableFilename(user, normalizedPath, filename, reservedNames);
            reservedNames.add(resolved);
            return resolved;
        }
    }

    private final class DirectoryNameTracker {
        private final WorkspaceUserContext user;
        private final Map<String, String> resolvedPathByOriginalPath = new HashMap<>();
        private final DuplicateNameTracker nameTracker;

        private DirectoryNameTracker(WorkspaceUserContext user) {
            this.user = user;
            this.nameTracker = new DuplicateNameTracker(user);
        }

        private String resolvePath(String targetRoot, String relativeParent, LinkedHashSet<String> directories) {
            String currentOriginalPath = normalizeDirectoryPath(targetRoot);
            String currentResolvedPath = currentOriginalPath;
            for (String segment : normalizeRelativePath(relativeParent).split("/")) {
                String nextOriginalPath = joinPath(currentOriginalPath, segment);
                String existingResolvedPath = resolvedPathByOriginalPath.get(nextOriginalPath);
                if (existingResolvedPath != null) {
                    currentOriginalPath = nextOriginalPath;
                    currentResolvedPath = existingResolvedPath;
                    directories.add(currentResolvedPath);
                    continue;
                }
                String resolvedSegment = nameTracker.resolve(currentResolvedPath, segment);
                currentOriginalPath = nextOriginalPath;
                currentResolvedPath = joinPath(currentResolvedPath, resolvedSegment);
                resolvedPathByOriginalPath.put(currentOriginalPath, currentResolvedPath);
                directories.add(currentResolvedPath);
            }
            return currentResolvedPath;
        }
    }
}
