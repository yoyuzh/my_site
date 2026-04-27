package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveBuildProgress;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveBuildProgressListener;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveExtractionResult;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveSummary;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.WorkspaceExternalImportProgress;
import com.yoyuzh.files.workspace.api.WorkspaceExternalImportProgressListener;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.api.WorkspaceZipArchive;
import com.yoyuzh.files.workspace.api.WorkspaceZipArchiveEntry;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class WorkspaceArchiveService {

    private static final long DEFAULT_MAX_ZIP_EXTRACT_BYTES = 500L * 1024 * 1024L;
    private static final long DEFAULT_MAX_ZIP_ENTRY_BYTES = 64L * 1024 * 1024L;
    private static final long MAX_ZIP_INFLATION_RATIO = 100L;
    private static final int MAX_ZIP_ENTRY_COUNT = 10_000;
    private static final int ZIP_READ_BUFFER_SIZE = 8192;
    private static final String ARCHIVE_READ_FAILED_MESSAGE = "压缩包读取失败";
    private static final String ARCHIVE_INVALID_CONTENT_MESSAGE = "压缩包内容不合法";
    private static final String DIRECTORY_ARCHIVE_FAILED_MESSAGE = "目录压缩失败";

    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;
    private final ContentBlobLifecycleApi contentBlobLifecycleApi;
    private final WorkspaceNodeRulesService workspaceNodeRulesService;
    private final WorkspaceDirectoryApi workspaceDirectoryApi;
    private final ExternalImportRulesService externalImportRulesService;
    private final WorkspaceFileIngressService workspaceFileIngressService;
    private final WorkspaceFileActivityService workspaceFileActivityService;

    WorkspaceArchiveService(StoredFileRepository storedFileRepository,
                            FileContentStorage fileContentStorage,
                            ContentBlobLifecycleApi contentBlobLifecycleApi,
                            WorkspaceNodeRulesService workspaceNodeRulesService,
                            WorkspaceDirectoryApi workspaceDirectoryApi,
                            ExternalImportRulesService externalImportRulesService,
                            WorkspaceFileIngressService workspaceFileIngressService,
                            WorkspaceFileActivityService workspaceFileActivityService) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
        this.contentBlobLifecycleApi = contentBlobLifecycleApi;
        this.workspaceNodeRulesService = workspaceNodeRulesService;
        this.workspaceDirectoryApi = workspaceDirectoryApi;
        this.externalImportRulesService = externalImportRulesService;
        this.workspaceFileIngressService = workspaceFileIngressService;
        this.workspaceFileActivityService = workspaceFileActivityService;
    }

    WorkspaceArchiveSummary summarizeArchiveSource(StoredFile source) {
        if (!source.isDirectory()) {
            return new WorkspaceArchiveSummary(1, 0);
        }
        String logicalPath = buildLogicalPath(source.getPath(), source.getFilename());
        List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(source.getUserId(), logicalPath);
        int directoryCount = 1 + (int) descendants.stream().filter(StoredFile::isDirectory).count();
        int fileCount = (int) descendants.stream().filter(file -> !file.isDirectory()).count();
        return new WorkspaceArchiveSummary(fileCount, directoryCount);
    }

    byte[] buildArchiveBytes(StoredFile source, WorkspaceArchiveBuildProgressListener progressListener) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            Set<String> createdEntries = new LinkedHashSet<>();
            ArchiveBuildProgressState progressState = createArchiveBuildProgressState(source, progressListener);
            reportArchiveProgress(progressState);
            if (source.isDirectory()) {
                writeDirectoryArchiveEntries(zipOutputStream, createdEntries, source, progressState);
            } else {
                writeFileArchiveEntry(zipOutputStream, createdEntries, source.getFilename(), source, progressState);
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, DIRECTORY_ARCHIVE_FAILED_MESSAGE);
        }
    }

    WorkspaceZipArchive readZipCompatibleArchive(StoredFile source, long maxFileSize) {
        long compressedSize = source.getSize() == null ? 0L : Math.max(0L, source.getSize());
        byte[] signature = new byte[4];
        try (BufferedInputStream bufferedStream = new BufferedInputStream(
                requireZipCompatibleArchiveStream(source, ARCHIVE_READ_FAILED_MESSAGE))) {
            bufferedStream.mark(signature.length);
            int signatureBytes = bufferedStream.read(signature, 0, signature.length);
            bufferedStream.reset();
            try (ZipInputStream zipInputStream = new ZipInputStream(bufferedStream, StandardCharsets.UTF_8)) {
                List<WorkspaceZipArchiveEntry> entries = new ArrayList<>();
                Map<String, Boolean> seenEntries = new HashMap<>();
                long totalUncompressedBytes = 0L;
                long maxInflatedBytes = resolveMaxZipInflatedBytes(compressedSize, maxFileSize);
                long maxEntryBytes = resolveMaxZipEntryBytes(maxFileSize);
                int entryCount = 0;
                ZipEntry entry = zipInputStream.getNextEntry();
                while (entry != null) {
                    String relativePath = normalizeZipCompatibleEntryPath(entry.getName());
                    if (StringUtils.hasText(relativePath)) {
                        entryCount += 1;
                        if (entryCount > MAX_ZIP_ENTRY_COUNT) {
                            throw invalidArchiveContent();
                        }
                        boolean directory = entry.isDirectory() || entry.getName().endsWith("/");
                        Boolean existingType = seenEntries.putIfAbsent(relativePath, directory);
                        if (existingType != null) {
                            throw invalidArchiveContent();
                        }
                        byte[] content = directory
                                ? new byte[0]
                                : readZipEntryContent(zipInputStream, maxInflatedBytes, maxEntryBytes, totalUncompressedBytes);
                        totalUncompressedBytes += content.length;
                        entries.add(new WorkspaceZipArchiveEntry(relativePath, directory, content));
                    }
                    entry = zipInputStream.getNextEntry();
                }
                if (entries.isEmpty() && !hasZipCompatibleSignature(signature, signatureBytes)) {
                    throw archiveReadFailed();
                }
                return new WorkspaceZipArchive(entries, detectCommonRootDirectoryName(entries));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw archiveReadFailed();
        }
    }

    WorkspaceArchiveExtractionResult extractZipCompatibleArchive(WorkspaceUserContext recipient,
                                                                StoredFile source,
                                                                String outputPath,
                                                                String outputDirectoryName,
                                                                WorkspaceExternalImportProgressListener progressListener,
                                                                long maxFileSize) {
        ZipExtractionPlan plan = buildZipExtractionPlan(source, outputPath, outputDirectoryName, maxFileSize);
        externalImportRulesService.validateBatch(
                recipient,
                plan.directories(),
                plan.files().stream()
                        .map(file -> new FileService.ExternalFileImport(
                                file.targetPath(),
                                file.filename(),
                                file.contentType(),
                                file.size(),
                                InputStream::nullInputStream
                        ))
                        .toList()
        );

        List<String> writtenBlobObjectKeys = new ArrayList<>();
        int totalDirectoryCount = plan.directories().size();
        int totalFileCount = plan.files().size();
        int processedDirectoryCount = 0;
        int processedFileCount = 0;
        Map<String, ZipExtractionTargetFile> fileTargets = new HashMap<>();
        for (ZipExtractionTargetFile file : plan.files()) {
            fileTargets.put(file.sourcePath(), file);
        }

        try {
            for (String directory : plan.directories()) {
                createDirectory(recipient, directory);
                processedDirectoryCount += 1;
                reportExternalImportProgress(
                        progressListener,
                        processedFileCount,
                        totalFileCount,
                        processedDirectoryCount,
                        totalDirectoryCount
                );
            }

            try (BufferedInputStream bufferedStream = new BufferedInputStream(
                    requireZipCompatibleArchiveStream(source, ARCHIVE_READ_FAILED_MESSAGE));
                 ZipInputStream zipInputStream = new ZipInputStream(bufferedStream, StandardCharsets.UTF_8)) {
                ZipEntry entry = zipInputStream.getNextEntry();
                while (entry != null) {
                    String relativePath = normalizeZipCompatibleEntryPath(entry.getName());
                    if (StringUtils.hasText(relativePath)) {
                        String sourcePath = stripCommonRootDirectory(relativePath, plan.commonRootDirectoryName());
                        ZipExtractionTargetFile targetFile = fileTargets.get(sourcePath);
                        if (targetFile != null) {
                            WorkspaceFileIngressService.CreatedFile createdFile = workspaceFileIngressService.importExternalFile(
                                    recipient,
                                    targetFile.targetPath(),
                                    targetFile.filename(),
                                    targetFile.contentType(),
                                    targetFile.size(),
                                    nonClosingZipEntryStream(zipInputStream),
                                    writtenBlobObjectKeys
                            );
                            recordCreatedFile(recipient, createdFile.normalizedPath(), createdFile.file());
                            processedFileCount += 1;
                            reportExternalImportProgress(
                                    progressListener,
                                    processedFileCount,
                                    totalFileCount,
                                    processedDirectoryCount,
                                    totalDirectoryCount
                            );
                        }
                    }
                    entry = zipInputStream.getNextEntry();
                }
            }
        } catch (RuntimeException ex) {
            workspaceFileIngressService.cleanupWrittenBlobs(writtenBlobObjectKeys, ex);
            throw ex;
        } catch (IOException ex) {
            IllegalStateException failure = new IllegalStateException("extract task failed to import archive content", ex);
            workspaceFileIngressService.cleanupWrittenBlobs(writtenBlobObjectKeys, failure);
            throw failure;
        }

        return new WorkspaceArchiveExtractionResult(
                plan.extractedPath(),
                plan.files().size(),
                plan.directories().size()
        );
    }

    private void createDirectory(WorkspaceUserContext user, String normalizedPath) {
        workspaceDirectoryApi.createDirectory(user.userId(), normalizedPath);
        workspaceFileActivityService.touchDirectories(user, workspaceNodeRulesService.extractParentPath(normalizedPath));
    }

    private void recordCreatedFile(WorkspaceUserContext user, String normalizedPath, RegisteredContentFile savedFile) {
        workspaceFileActivityService.afterFileCreated(user, normalizedPath, savedFile);
    }

    private InputStream nonClosingZipEntryStream(ZipInputStream zipInputStream) {
        return new FilterInputStream(zipInputStream) {
            @Override
            public void close() {
                // Each entry shares the same ZipInputStream; closing the per-entry view would abort subsequent entries.
            }
        };
    }

    private ZipExtractionPlan buildZipExtractionPlan(StoredFile source,
                                                     String outputPath,
                                                     String outputDirectoryName,
                                                     long maxFileSize) {
        ZipArchiveMetadata archive = readZipCompatibleArchiveMetadata(source, maxFileSize);
        List<ZipItemMetadata> items = archive.entries().stream()
                .map(entry -> toZipItemMetadata(entry, archive.commonRootDirectoryName()))
                .filter(item -> StringUtils.hasText(item.path()))
                .toList();
        if (items.isEmpty()) {
            throw new IllegalStateException("extract task archive is empty");
        }

        String normalizedOutputPath = workspaceNodeRulesService.normalizeDirectoryPath(outputPath);
        String normalizedOutputDirectoryName = workspaceNodeRulesService.normalizeLeafName(outputDirectoryName);
        if (shouldExtractSingleFileToParent(items, normalizedOutputDirectoryName)) {
            return buildSingleFileExtractionPlan(archive.commonRootDirectoryName(), items.get(0), normalizedOutputPath, normalizedOutputDirectoryName);
        }
        return buildDirectoryExtractionPlan(archive.commonRootDirectoryName(), items, normalizedOutputPath, normalizedOutputDirectoryName);
    }

    private ZipExtractionPlan buildSingleFileExtractionPlan(String commonRootDirectoryName,
                                                            ZipItemMetadata fileItem,
                                                            String normalizedOutputPath,
                                                            String normalizedOutputDirectoryName) {
        return new ZipExtractionPlan(
                commonRootDirectoryName,
                List.of(),
                List.of(new ZipExtractionTargetFile(
                        fileItem.path(),
                        normalizedOutputPath,
                        normalizedOutputDirectoryName,
                        fileItem.contentType(),
                        fileItem.size()
                )),
                normalizedOutputPath
        );
    }

    private ZipExtractionPlan buildDirectoryExtractionPlan(String commonRootDirectoryName,
                                                           List<ZipItemMetadata> items,
                                                           String normalizedOutputPath,
                                                           String normalizedOutputDirectoryName) {
        String rootPath = joinPath(normalizedOutputPath, normalizedOutputDirectoryName);
        LinkedHashSet<String> directories = new LinkedHashSet<>();
        directories.add(rootPath);
        List<ZipExtractionTargetFile> files = new ArrayList<>();
        for (ZipItemMetadata item : items) {
            if (item.directory()) {
                directories.add(joinPath(rootPath, trimTrailingSlash(item.path())));
                continue;
            }
            String relativeParent = workspaceNodeRulesService.extractParentPath(item.path());
            String targetParent = StringUtils.hasText(relativeParent) ? joinPath(rootPath, relativeParent) : rootPath;
            collectParentDirectories(directories, rootPath, relativeParent);
            files.add(new ZipExtractionTargetFile(
                    item.path(),
                    targetParent,
                    workspaceNodeRulesService.extractLeafName(item.path()),
                    item.contentType(),
                    item.size()
            ));
        }
        return new ZipExtractionPlan(commonRootDirectoryName, List.copyOf(directories), List.copyOf(files), rootPath);
    }

    private ZipArchiveMetadata readZipCompatibleArchiveMetadata(StoredFile source, long maxFileSize) {
        byte[] signature = new byte[4];
        long compressedSize = getRequiredBlob(source).size();
        try (BufferedInputStream bufferedStream = new BufferedInputStream(
                requireZipCompatibleArchiveStream(source, ARCHIVE_READ_FAILED_MESSAGE))) {
            bufferedStream.mark(signature.length);
            int signatureBytes = bufferedStream.read(signature, 0, signature.length);
            bufferedStream.reset();
            try (ZipInputStream zipInputStream = new ZipInputStream(bufferedStream, StandardCharsets.UTF_8)) {
                List<ZipMetadataEntry> entries = new ArrayList<>();
                Map<String, Boolean> seenEntries = new HashMap<>();
                long totalUncompressedBytes = 0L;
                long maxInflatedBytes = resolveMaxZipInflatedBytes(compressedSize, maxFileSize);
                long maxEntryBytes = resolveMaxZipEntryBytes(maxFileSize);
                int entryCount = 0;
                ZipEntry entry = zipInputStream.getNextEntry();
                while (entry != null) {
                    String relativePath = normalizeZipCompatibleEntryPath(entry.getName());
                    if (StringUtils.hasText(relativePath)) {
                        entryCount += 1;
                        if (entryCount > MAX_ZIP_ENTRY_COUNT) {
                            throw invalidArchiveContent();
                        }
                        boolean directory = entry.isDirectory() || entry.getName().endsWith("/");
                        Boolean existingType = seenEntries.putIfAbsent(relativePath, directory);
                        if (existingType != null) {
                            throw invalidArchiveContent();
                        }
                        long size = directory
                                ? 0L
                                : readZipEntryBytes(zipInputStream, maxInflatedBytes, maxEntryBytes, totalUncompressedBytes, null);
                        totalUncompressedBytes += size;
                        entries.add(new ZipMetadataEntry(
                                relativePath,
                                directory,
                                size,
                                WorkspaceContentTypeResolver.guessContentType(relativePath)
                        ));
                    }
                    entry = zipInputStream.getNextEntry();
                }
                if (entries.isEmpty() && !hasZipCompatibleSignature(signature, signatureBytes)) {
                    throw archiveReadFailed();
                }
                return new ZipArchiveMetadata(entries, detectCommonRootDirectoryNameFromCandidates(entries));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw archiveReadFailed();
        }
    }

    private void writeDirectoryArchiveEntries(ZipOutputStream zipOutputStream,
                                              Set<String> createdEntries,
                                              StoredFile directory,
                                              ArchiveBuildProgressState progressState) throws IOException {
        String logicalPath = buildLogicalPath(directory.getPath(), directory.getFilename());
        List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(directory.getUserId(), logicalPath)
                .stream()
                .sorted(Comparator.comparing(StoredFile::getPath).thenComparing(StoredFile::getFilename))
                .toList();
        writeDirectoryEntry(zipOutputStream, createdEntries, directory.getFilename() + "/", progressState);

        for (StoredFile descendant : descendants) {
            String entryName = buildZipEntryName(directory.getFilename(), logicalPath, descendant);
            if (descendant.isDirectory()) {
                writeDirectoryEntry(zipOutputStream, createdEntries, entryName + "/", progressState);
                continue;
            }
            writeFileArchiveEntry(zipOutputStream, createdEntries, entryName, descendant, progressState);
        }
    }

    private void writeFileArchiveEntry(ZipOutputStream zipOutputStream,
                                       Set<String> createdEntries,
                                       String entryName,
                                       StoredFile file,
                                       ArchiveBuildProgressState progressState) throws IOException {
        ensureParentDirectoryEntries(zipOutputStream, createdEntries, entryName, progressState);
        writeFileEntry(
                zipOutputStream,
                createdEntries,
                entryName,
                progressState,
                fileContentStorage.readBlob(getRequiredBlob(file).objectKey())
        );
    }

    private String buildZipEntryName(String rootDirectoryName, String rootLogicalPath, StoredFile storedFile) {
        StringBuilder entryName = new StringBuilder(rootDirectoryName).append('/');
        if (!storedFile.getPath().equals(rootLogicalPath)) {
            entryName.append(storedFile.getPath().substring(rootLogicalPath.length() + 1)).append('/');
        }
        entryName.append(storedFile.getFilename());
        return entryName.toString();
    }

    private String normalizeZipCompatibleEntryPath(String entryName) {
        String normalized = entryName == null ? "" : entryName.trim().replace("\\", "/");
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.startsWith("/")) {
            throw invalidArchiveContent();
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!StringUtils.hasText(normalized)) {
            return "";
        }

        StringBuilder sanitized = new StringBuilder();
        for (String segment : normalized.split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw invalidArchiveContent();
            }
            if (sanitized.length() > 0) {
                sanitized.append('/');
            }
            sanitized.append(workspaceNodeRulesService.normalizeLeafName(segment));
        }
        return sanitized.toString();
    }

    private String detectCommonRootDirectoryName(List<WorkspaceZipArchiveEntry> entries) {
        return detectCommonRootDirectoryNameFromCandidates(entries.stream()
                .map(entry -> new ZipRootCandidate(entry.relativePath(), entry.directory()))
                .toList());
    }

    private String detectCommonRootDirectoryNameFromCandidates(List<? extends ZipRootCandidateView> entries) {
        String candidate = null;
        boolean hasNestedEntry = false;
        boolean hasDirectoryCandidate = false;
        for (ZipRootCandidateView entry : entries) {
            String relativePath = entry.relativePath();
            int slashIndex = relativePath.indexOf('/');
            String topSegment = slashIndex >= 0 ? relativePath.substring(0, slashIndex) : relativePath;
            if (candidate == null) {
                candidate = topSegment;
            } else if (!candidate.equals(topSegment)) {
                return null;
            }
            if (slashIndex >= 0) {
                hasNestedEntry = true;
            }
            if (entry.directory() && candidate.equals(relativePath)) {
                hasDirectoryCandidate = true;
            }
            if (!entry.directory() && candidate.equals(relativePath)) {
                return null;
            }
        }
        if (!hasNestedEntry && !hasDirectoryCandidate) {
            return null;
        }
        return candidate;
    }

    private boolean hasZipCompatibleSignature(byte[] archiveBytes, int bytesRead) {
        if (archiveBytes == null || bytesRead < 4) {
            return false;
        }
        return archiveBytes[0] == 'P'
                && archiveBytes[1] == 'K'
                && (archiveBytes[2] == 3 || archiveBytes[2] == 5 || archiveBytes[2] == 7)
                && (archiveBytes[3] == 4 || archiveBytes[3] == 6 || archiveBytes[3] == 8);
    }

    private byte[] readZipEntryContent(ZipInputStream zipInputStream,
                                       long maxInflatedBytes,
                                       long maxEntryBytes,
                                       long currentTotalBytes) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            readZipEntryBytes(zipInputStream, maxInflatedBytes, maxEntryBytes, currentTotalBytes, outputStream);
            return outputStream.toByteArray();
        }
    }

    private long readZipEntryBytes(ZipInputStream zipInputStream,
                                   long maxInflatedBytes,
                                   long maxEntryBytes,
                                   long currentTotalBytes,
                                   ByteArrayOutputStream outputStream) throws IOException {
        byte[] buffer = new byte[ZIP_READ_BUFFER_SIZE];
        int read;
        long entryBytes = 0L;
        while ((read = zipInputStream.read(buffer)) != -1) {
            entryBytes += read;
            if (entryBytes > maxEntryBytes) {
                throw invalidArchiveContent();
            }
            if (currentTotalBytes + entryBytes > maxInflatedBytes) {
                throw invalidArchiveContent();
            }
            if (outputStream != null) {
                outputStream.write(buffer, 0, read);
            }
        }
        return entryBytes;
    }

    private ZipItemMetadata toZipItemMetadata(ZipMetadataEntry entry, String commonRootDirectoryName) {
        String path = stripCommonRootDirectory(entry.relativePath(), commonRootDirectoryName);
        return new ZipItemMetadata(path, entry.directory(), entry.size(), entry.contentType());
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

    private boolean shouldExtractSingleFileToParent(List<ZipItemMetadata> items, String outputDirectoryName) {
        if (items.size() != 1) {
            return false;
        }
        ZipItemMetadata item = items.get(0);
        return !item.directory()
                && !item.path().contains("/")
                && outputDirectoryName.equals(item.path());
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

    private String joinPath(String parent, String leaf) {
        return buildTargetLogicalPath(workspaceNodeRulesService.normalizeDirectoryPath(parent), trimSlashes(leaf));
    }

    private String buildTargetLogicalPath(String normalizedTargetPath, String filename) {
        return workspaceNodeRulesService.buildTargetLogicalPath(normalizedTargetPath, filename);
    }

    private String trimSlashes(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String trimTrailingSlash(String value) {
        return StringUtils.hasLength(value) && value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    private long resolveMaxZipEntryBytes(long maxFileSize) {
        long configuredLimit = maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_ZIP_EXTRACT_BYTES;
        return Math.max(1L, Math.min(configuredLimit, DEFAULT_MAX_ZIP_ENTRY_BYTES));
    }

    private long resolveMaxZipInflatedBytes(long compressedSize, long maxFileSize) {
        long configuredLimit = maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_ZIP_EXTRACT_BYTES;
        long ratioLimit;
        if (compressedSize <= 0L || compressedSize > Long.MAX_VALUE / MAX_ZIP_INFLATION_RATIO) {
            ratioLimit = configuredLimit;
        } else {
            ratioLimit = compressedSize * MAX_ZIP_INFLATION_RATIO;
        }
        return Math.max(1L, Math.min(configuredLimit, ratioLimit));
    }

    private ArchiveBuildProgressState createArchiveBuildProgressState(StoredFile source,
                                                                      WorkspaceArchiveBuildProgressListener progressListener) {
        if (progressListener == null) {
            return null;
        }
        WorkspaceArchiveSummary summary = summarizeArchiveSource(source);
        return new ArchiveBuildProgressState(progressListener, summary.fileCount(), summary.directoryCount());
    }

    private void reportArchiveProgress(ArchiveBuildProgressState progressState) {
        if (progressState == null) {
            return;
        }
        progressState.listener.onProgress(new WorkspaceArchiveBuildProgress(
                progressState.processedFileCount,
                progressState.totalFileCount,
                progressState.processedDirectoryCount,
                progressState.totalDirectoryCount
        ));
    }

    private void reportExternalImportProgress(WorkspaceExternalImportProgressListener progressListener,
                                              int processedFileCount,
                                              int totalFileCount,
                                              int processedDirectoryCount,
                                              int totalDirectoryCount) {
        if (progressListener == null) {
            return;
        }
        progressListener.onProgress(new WorkspaceExternalImportProgress(
                processedFileCount,
                totalFileCount,
                processedDirectoryCount,
                totalDirectoryCount
        ));
    }

    private void ensureParentDirectoryEntries(ZipOutputStream zipOutputStream,
                                              Set<String> createdEntries,
                                              String entryName,
                                              ArchiveBuildProgressState progressState) throws IOException {
        int slashIndex = entryName.indexOf('/');
        while (slashIndex >= 0) {
            writeDirectoryEntry(zipOutputStream, createdEntries, entryName.substring(0, slashIndex + 1), progressState);
            slashIndex = entryName.indexOf('/', slashIndex + 1);
        }
    }

    private void writeDirectoryEntry(ZipOutputStream zipOutputStream,
                                     Set<String> createdEntries,
                                     String entryName,
                                     ArchiveBuildProgressState progressState) throws IOException {
        if (!createdEntries.add(entryName)) {
            return;
        }

        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.closeEntry();
        if (progressState != null) {
            progressState.processedDirectoryCount += 1;
            reportArchiveProgress(progressState);
        }
    }

    private void writeFileEntry(ZipOutputStream zipOutputStream,
                                Set<String> createdEntries,
                                String entryName,
                                ArchiveBuildProgressState progressState,
                                byte[] content) throws IOException {
        if (!createdEntries.add(entryName)) {
            return;
        }

        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
        if (progressState != null) {
            progressState.processedFileCount += 1;
            reportArchiveProgress(progressState);
        }
    }

    private ContentBlobReference getRequiredBlob(StoredFile storedFile) {
        return contentBlobLifecycleApi.requireBlobReference(storedFile.getBlobId(), storedFile.isDirectory());
    }

    private InputStream requireZipCompatibleArchiveStream(StoredFile source, String failureMessage) {
        return Optional.ofNullable(fileContentStorage.readBlobStream(getRequiredBlob(source).objectKey()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ARCHIVE_READ_FAILED, failureMessage));
    }

    private String buildLogicalPath(String path, String filename) {
        return "/".equals(path) ? "/" + filename : path + "/" + filename;
    }

    private BusinessException archiveReadFailed() {
        return new BusinessException(ErrorCode.ARCHIVE_READ_FAILED, ARCHIVE_READ_FAILED_MESSAGE);
    }

    private BusinessException invalidArchiveContent() {
        return new BusinessException(ErrorCode.ARCHIVE_READ_FAILED, ARCHIVE_INVALID_CONTENT_MESSAGE);
    }

    private interface ZipRootCandidateView {
        String relativePath();

        boolean directory();
    }

    private record ZipRootCandidate(String relativePath, boolean directory) implements ZipRootCandidateView {
    }

    private record ZipMetadataEntry(String relativePath,
                                    boolean directory,
                                    long size,
                                    String contentType) implements ZipRootCandidateView {
    }

    private record ZipArchiveMetadata(List<ZipMetadataEntry> entries, String commonRootDirectoryName) {
    }

    private record ZipItemMetadata(String path,
                                   boolean directory,
                                   long size,
                                   String contentType) {
    }

    private record ZipExtractionTargetFile(String sourcePath,
                                           String targetPath,
                                           String filename,
                                           String contentType,
                                           long size) {
    }

    private record ZipExtractionPlan(String commonRootDirectoryName,
                                     List<String> directories,
                                     List<ZipExtractionTargetFile> files,
                                     String extractedPath) {
    }

    private static final class ArchiveBuildProgressState {
        private final WorkspaceArchiveBuildProgressListener listener;
        private final int totalFileCount;
        private final int totalDirectoryCount;
        private int processedFileCount;
        private int processedDirectoryCount;

        private ArchiveBuildProgressState(WorkspaceArchiveBuildProgressListener listener,
                                          int totalFileCount,
                                          int totalDirectoryCount) {
            this.listener = listener;
            this.totalFileCount = totalFileCount;
            this.totalDirectoryCount = totalDirectoryCount;
        }
    }
}
