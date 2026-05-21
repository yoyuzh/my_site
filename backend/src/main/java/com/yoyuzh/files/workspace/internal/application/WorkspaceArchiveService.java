package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobReadApi;
import com.yoyuzh.files.content.api.ContentBlobReadResult;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveEntry;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveBuildProgress;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveBuildProgressListener;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveExtractionResult;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveListing;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveSummary;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadResult;
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
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class WorkspaceArchiveService {

    private static final long DEFAULT_MAX_ZIP_EXTRACT_BYTES = 500L * 1024 * 1024L;
    private static final long DEFAULT_MAX_ZIP_ENTRY_BYTES = 64L * 1024 * 1024L;
    private static final long MAX_ZIP_INFLATION_RATIO = 100L;
    private static final int MAX_ZIP_ENTRY_COUNT = 10_000;
    private static final int ZIP_READ_BUFFER_SIZE = 8192;
    private static final String BSDTAR_COMMAND = "bsdtar";
    private static final long ARCHIVE_TOOL_TIMEOUT_SECONDS = 120L;
    private static final String ARCHIVE_READ_FAILED_MESSAGE = "压缩包读取失败";
    private static final String ARCHIVE_INVALID_CONTENT_MESSAGE = "压缩包内容不合法";
    private static final String ARCHIVE_ENTRY_INVALID_MESSAGE = "压缩包条目不存在或不可读取";
    private static final String DIRECTORY_ARCHIVE_FAILED_MESSAGE = "目录压缩失败";
    private static final Charset ZIP_PRIMARY_CHARSET = StandardCharsets.UTF_8;
    private static final Charset ZIP_FALLBACK_CHARSET = Charset.forName("GBK");
    private static final String RAR_TOOL_MISSING_MESSAGE = "RAR 解压依赖未安装";

    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;
    private final ContentBlobLifecycleApi contentBlobLifecycleApi;
    private final ContentBlobReadApi contentBlobReadApi;
    private final WorkspaceNodeRulesService workspaceNodeRulesService;
    private final WorkspaceDirectoryApi workspaceDirectoryApi;
    private final ExternalImportRulesService externalImportRulesService;
    private final WorkspaceFileIngressService workspaceFileIngressService;
    private final WorkspaceFileActivityService workspaceFileActivityService;
    private String archiveToolCommand = BSDTAR_COMMAND;

    WorkspaceArchiveService(StoredFileRepository storedFileRepository,
                            FileContentStorage fileContentStorage,
                            ContentBlobLifecycleApi contentBlobLifecycleApi,
                            ContentBlobReadApi contentBlobReadApi,
                            WorkspaceNodeRulesService workspaceNodeRulesService,
                            WorkspaceDirectoryApi workspaceDirectoryApi,
                            ExternalImportRulesService externalImportRulesService,
                            WorkspaceFileIngressService workspaceFileIngressService,
                            WorkspaceFileActivityService workspaceFileActivityService) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
        this.contentBlobLifecycleApi = contentBlobLifecycleApi;
        this.contentBlobReadApi = contentBlobReadApi;
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

    WorkspaceArchiveListing readArchive(StoredFile source, long maxFileSize) {
        ArchiveFormat format = resolveArchiveFormat(source);
        if (format == ArchiveFormat.ZIP_COMPATIBLE) {
            return toArchiveListing(readZipCompatibleArchiveMetadata(source, maxFileSize));
        }
        try {
            return withExtractedArchive(source, format, maxFileSize, extractedRoot ->
                    toArchiveListing(scanExtractedArchiveTree(extractedRoot, maxFileSize)));
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw archiveReadFailed();
        }
    }

    WorkspaceDownloadResult downloadArchiveEntry(StoredFile source, String entryPath, long maxFileSize) {
        String normalizedEntryPath = normalizeZipCompatibleEntryPath(entryPath);
        ArchiveFormat format = resolveArchiveFormat(source);
        if (format == ArchiveFormat.ZIP_COMPATIBLE) {
            return downloadZipCompatibleArchiveEntry(source, normalizedEntryPath, maxFileSize);
        }
        try {
            return withExtractedArchive(source, format, maxFileSize, extractedRoot -> {
                ExtractedArchiveTree archiveTree = scanExtractedArchiveTree(extractedRoot, maxFileSize);
                ExtractedArchiveEntry archiveEntry = archiveTree.entries().stream()
                        .filter(entry -> entry.relativePath().equals(normalizedEntryPath))
                        .findFirst()
                        .orElseThrow(this::invalidArchiveEntry);
                if (archiveEntry.directory()) {
                    throw invalidArchiveEntry();
                }
                byte[] content = readFileContent(resolveArchiveEntryFile(extractedRoot, normalizedEntryPath), maxFileSize);
                return WorkspaceDownloadResult.inline(
                        extractLeafName(normalizedEntryPath),
                        archiveEntry.contentType(),
                        content
                );
            });
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw archiveReadFailed();
        }
    }

    WorkspaceArchiveExtractionResult extractArchive(WorkspaceUserContext recipient,
                                                    StoredFile source,
                                                    String outputPath,
                                                    String outputDirectoryName,
                                                    WorkspaceExternalImportProgressListener progressListener,
                                                    long maxFileSize) {
        ArchiveFormat format = resolveArchiveFormat(source);
        if (format == ArchiveFormat.ZIP_COMPATIBLE) {
            return extractZipCompatibleArchive(
                    recipient,
                    source,
                    outputPath,
                    outputDirectoryName,
                    progressListener,
                    maxFileSize
            );
        }
        List<String> writtenBlobObjectKeys = new ArrayList<>();
        try {
            return withExtractedArchive(source, format, maxFileSize, extractedRoot -> {
                ExtractedArchiveTree archiveTree = scanExtractedArchiveTree(extractedRoot, maxFileSize);
                ArchiveExtractionPlan plan = buildArchiveExtractionPlan(
                        archiveTree.commonRootDirectoryName(),
                        archiveTree.entries(),
                        outputPath,
                        outputDirectoryName
                );
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

                int totalDirectoryCount = plan.directories().size();
                int totalFileCount = plan.files().size();
                int processedDirectoryCount = 0;
                int processedFileCount = 0;

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

                for (ArchiveExtractionTargetFile file : plan.files()) {
                    try (InputStream inputStream = Files.newInputStream(resolveArchiveEntryFile(extractedRoot, file.archiveRelativePath()))) {
                        WorkspaceFileIngressService.CreatedFile createdFile = workspaceFileIngressService.importExternalFile(
                                recipient,
                                file.targetPath(),
                                file.filename(),
                                file.contentType(),
                                file.size(),
                                inputStream,
                                writtenBlobObjectKeys
                        );
                        recordCreatedFile(recipient, createdFile.normalizedPath(), createdFile.file());
                    }
                    processedFileCount += 1;
                    reportExternalImportProgress(
                            progressListener,
                            processedFileCount,
                            totalFileCount,
                            processedDirectoryCount,
                            totalDirectoryCount
                    );
                }

                return new WorkspaceArchiveExtractionResult(
                        plan.extractedPath(),
                        plan.files().size(),
                        plan.directories().size()
                );
            });
        } catch (BusinessException ex) {
            workspaceFileIngressService.cleanupWrittenBlobs(writtenBlobObjectKeys, ex);
            throw ex;
        } catch (RuntimeException ex) {
            workspaceFileIngressService.cleanupWrittenBlobs(writtenBlobObjectKeys, ex);
            throw ex;
        } catch (IOException ex) {
            IllegalStateException failure = new IllegalStateException("extract task failed to import archive content", ex);
            workspaceFileIngressService.cleanupWrittenBlobs(writtenBlobObjectKeys, failure);
            throw failure;
        }
    }

    WorkspaceZipArchive readZipCompatibleArchive(StoredFile source, long maxFileSize) {
        return readZipCompatibleArchive(source, maxFileSize, ZIP_PRIMARY_CHARSET, true);
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

    private WorkspaceArchiveListing toArchiveListing(WorkspaceZipArchive archive) {
        return new WorkspaceArchiveListing(
                archive.entries().stream()
                        .map(entry -> new WorkspaceArchiveEntry(
                                entry.relativePath(),
                                entry.directory(),
                                entry.directory() ? 0L : entry.content().length,
                                entry.directory() ? "inode/directory" : WorkspaceContentTypeResolver.guessContentType(entry.relativePath())
                        ))
                        .toList(),
                archive.commonRootDirectoryName()
        );
    }

    private WorkspaceArchiveListing toArchiveListing(ExtractedArchiveTree archive) {
        return new WorkspaceArchiveListing(
                archive.entries().stream()
                        .map(entry -> new WorkspaceArchiveEntry(
                                entry.relativePath(),
                                entry.directory(),
                                entry.size(),
                                entry.contentType()
                        ))
                        .toList(),
                archive.commonRootDirectoryName()
        );
    }

    private WorkspaceArchiveListing toArchiveListing(ZipArchiveMetadata archive) {
        return new WorkspaceArchiveListing(
                archive.entries().stream()
                        .map(entry -> new WorkspaceArchiveEntry(
                                entry.relativePath(),
                                entry.directory(),
                                entry.size(),
                                entry.contentType()
                        ))
                        .toList(),
                archive.commonRootDirectoryName()
        );
    }

    private <T> T withExtractedArchive(StoredFile source,
                                       ArchiveFormat format,
                                       long maxFileSize,
                                       ExtractedArchiveCallback<T> callback) throws IOException {
        Path archivePath = materializeArchiveToTempFile(source, format);
        Path extractedRoot = Files.createTempDirectory("workspace-archive-");
        try {
            extractArchiveToDirectory(archivePath, extractedRoot, source.getFilename(), format, maxFileSize);
            return callback.apply(extractedRoot);
        } finally {
            deleteQuietly(extractedRoot);
            deleteQuietly(archivePath);
        }
    }

    private Path materializeArchiveToTempFile(StoredFile source, ArchiveFormat format) throws IOException {
        Path archivePath = Files.createTempFile("workspace-archive-", format.tempFileSuffix());
        try (InputStream inputStream = requireZipCompatibleArchiveStream(source, ARCHIVE_READ_FAILED_MESSAGE)) {
            Files.copy(inputStream, archivePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return archivePath;
    }

    private void extractArchiveToDirectory(Path archivePath,
                                           Path extractedRoot,
                                           String sourceFilename,
                                           ArchiveFormat format,
                                           long maxFileSize) throws IOException {
        switch (format) {
            case TAR -> extractTarArchive(archivePath, extractedRoot, ArchiveFormat.TAR, maxFileSize);
            case TAR_GZ -> extractTarArchive(archivePath, extractedRoot, ArchiveFormat.TAR_GZ, maxFileSize);
            case TAR_BZ2 -> extractTarArchive(archivePath, extractedRoot, ArchiveFormat.TAR_BZ2, maxFileSize);
            case TAR_XZ -> extractTarArchive(archivePath, extractedRoot, ArchiveFormat.TAR_XZ, maxFileSize);
            case GZIP -> extractSingleFileArchive(archivePath, extractedRoot, deriveSingleFileEntryName(sourceFilename, format), format, maxFileSize);
            case BZIP2 -> extractSingleFileArchive(archivePath, extractedRoot, deriveSingleFileEntryName(sourceFilename, format), format, maxFileSize);
            case XZ -> extractSingleFileArchive(archivePath, extractedRoot, deriveSingleFileEntryName(sourceFilename, format), format, maxFileSize);
            case SEVEN_Z -> extractSevenZipArchive(archivePath, extractedRoot, maxFileSize);
            case RAR -> extractRarArchive(archivePath, extractedRoot);
            case ZIP_COMPATIBLE -> throw archiveReadFailed();
        }
    }

    private void extractTarArchive(Path archivePath,
                                   Path extractedRoot,
                                   ArchiveFormat format,
                                   long maxFileSize) throws IOException {
        Map<String, Boolean> seenEntries = new HashMap<>();
        long maxInflatedBytes = resolveMaxArchiveExtractBytes(maxFileSize);
        long maxEntryBytes = resolveMaxZipEntryBytes(maxFileSize);
        long totalExtractedBytes = 0L;
        int entryCount = 0;
        try (InputStream fileInputStream = new BufferedInputStream(Files.newInputStream(archivePath));
             InputStream archiveInputStream = wrapTarArchiveInputStream(fileInputStream, format);
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(archiveInputStream, StandardCharsets.UTF_8.name())) {
            TarArchiveEntry entry = tarInputStream.getNextTarEntry();
            while (entry != null) {
                if (entry.isSymbolicLink() || entry.isLink()) {
                    throw invalidArchiveContent();
                }
                String relativePath = normalizeZipCompatibleEntryPath(entry.getName());
                if (StringUtils.hasText(relativePath)) {
                    entryCount += 1;
                    if (entryCount > MAX_ZIP_ENTRY_COUNT) {
                        throw invalidArchiveContent();
                    }
                    boolean directory = entry.isDirectory();
                    Boolean existingType = seenEntries.putIfAbsent(relativePath, directory);
                    if (existingType != null) {
                        throw invalidArchiveContent();
                    }
                    Path outputPath = resolveArchiveEntryFile(extractedRoot, relativePath);
                    if (directory) {
                        Files.createDirectories(outputPath);
                    } else {
                        Files.createDirectories(outputPath.getParent());
                        try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                            long entryBytes = copyStreamWithLimits(
                                    tarInputStream,
                                    outputStream,
                                    maxEntryBytes,
                                    maxInflatedBytes,
                                    totalExtractedBytes
                            );
                            totalExtractedBytes += entryBytes;
                        }
                    }
                }
                entry = tarInputStream.getNextTarEntry();
            }
        }
    }

    private InputStream wrapTarArchiveInputStream(InputStream fileInputStream, ArchiveFormat format) throws IOException {
        return switch (format) {
            case TAR -> fileInputStream;
            case TAR_GZ -> new GzipCompressorInputStream(fileInputStream);
            case TAR_BZ2 -> new BZip2CompressorInputStream(fileInputStream);
            case TAR_XZ -> new XZCompressorInputStream(fileInputStream);
            default -> throw archiveReadFailed();
        };
    }

    private void extractSingleFileArchive(Path archivePath,
                                          Path extractedRoot,
                                          String entryName,
                                          ArchiveFormat format,
                                          long maxFileSize) throws IOException {
        Path outputPath = resolveArchiveEntryFile(extractedRoot, entryName);
        Files.createDirectories(outputPath.getParent());
        try (InputStream fileInputStream = new BufferedInputStream(Files.newInputStream(archivePath));
             InputStream archiveInputStream = wrapSingleFileArchiveInputStream(fileInputStream, format);
             OutputStream outputStream = Files.newOutputStream(outputPath)) {
            copyStreamWithLimits(
                    archiveInputStream,
                    outputStream,
                    resolveMaxZipEntryBytes(maxFileSize),
                    resolveMaxArchiveExtractBytes(maxFileSize),
                    0L
            );
        }
    }

    private InputStream wrapSingleFileArchiveInputStream(InputStream fileInputStream, ArchiveFormat format) throws IOException {
        return switch (format) {
            case GZIP -> new GzipCompressorInputStream(fileInputStream);
            case BZIP2 -> new BZip2CompressorInputStream(fileInputStream);
            case XZ -> new XZCompressorInputStream(fileInputStream);
            default -> throw archiveReadFailed();
        };
    }

    private void extractSevenZipArchive(Path archivePath, Path extractedRoot, long maxFileSize) throws IOException {
        Map<String, Boolean> seenEntries = new HashMap<>();
        long maxInflatedBytes = resolveMaxArchiveExtractBytes(maxFileSize);
        long maxEntryBytes = resolveMaxZipEntryBytes(maxFileSize);
        long totalExtractedBytes = 0L;
        int entryCount = 0;
        byte[] buffer = new byte[ZIP_READ_BUFFER_SIZE];
        try (SevenZFile sevenZFile = new SevenZFile(archivePath.toFile())) {
            SevenZArchiveEntry entry = sevenZFile.getNextEntry();
            while (entry != null) {
                if (entry.isAntiItem()) {
                    throw invalidArchiveContent();
                }
                String relativePath = normalizeZipCompatibleEntryPath(entry.getName());
                if (StringUtils.hasText(relativePath)) {
                    entryCount += 1;
                    if (entryCount > MAX_ZIP_ENTRY_COUNT) {
                        throw invalidArchiveContent();
                    }
                    boolean directory = entry.isDirectory();
                    Boolean existingType = seenEntries.putIfAbsent(relativePath, directory);
                    if (existingType != null) {
                        throw invalidArchiveContent();
                    }
                    Path outputPath = resolveArchiveEntryFile(extractedRoot, relativePath);
                    if (directory) {
                        Files.createDirectories(outputPath);
                    } else {
                        Files.createDirectories(outputPath.getParent());
                        try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                            long entryBytes = 0L;
                            int read;
                            while ((read = sevenZFile.read(buffer, 0, buffer.length)) > 0) {
                                entryBytes += read;
                                if (entryBytes > maxEntryBytes || totalExtractedBytes + entryBytes > maxInflatedBytes) {
                                    throw invalidArchiveContent();
                                }
                                outputStream.write(buffer, 0, read);
                            }
                            totalExtractedBytes += entryBytes;
                        }
                    }
                }
                entry = sevenZFile.getNextEntry();
            }
        }
    }

    private void extractRarArchive(Path archivePath, Path extractedRoot) throws IOException {
        validateRarArchiveEntryNames(archivePath);
        executeArchiveTool(List.of(
                archiveToolCommand,
                "-xf",
                archivePath.toString(),
                "-C",
                extractedRoot.toString()
        ));
    }

    private void validateRarArchiveEntryNames(Path archivePath) throws IOException {
        List<String> lines = executeArchiveTool(List.of(archiveToolCommand, "-tf", archivePath.toString()));
        Map<String, Boolean> seenEntries = new HashMap<>();
        int entryCount = 0;
        for (String line : lines) {
            String relativePath = normalizeZipCompatibleEntryPath(line);
            if (!StringUtils.hasText(relativePath)) {
                continue;
            }
            entryCount += 1;
            if (entryCount > MAX_ZIP_ENTRY_COUNT) {
                throw invalidArchiveContent();
            }
            boolean directory = line.endsWith("/");
            Boolean existingType = seenEntries.putIfAbsent(relativePath, directory);
            if (existingType != null) {
                throw invalidArchiveContent();
            }
        }
    }

    private List<String> executeArchiveTool(List<String> command) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ex) {
            if (isMissingArchiveTool(ex)) {
                throw rarToolMissing();
            }
            throw ex;
        }
        List<String> lines;
        try (Stream<String> output = new BufferedReader(new InputStreamReader(
                process.getInputStream(),
                StandardCharsets.UTF_8
        )).lines()) {
            lines = output.toList();
        }
        try {
            boolean finished = process.waitFor(ARCHIVE_TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw archiveReadFailed();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw archiveReadFailed();
        }
        if (process.exitValue() != 0) {
            throw archiveReadFailed();
        }
        return lines;
    }

    private ExtractedArchiveTree scanExtractedArchiveTree(Path extractedRoot, long maxFileSize) throws IOException {
        List<ExtractedArchiveEntry> entries = new ArrayList<>();
        long maxInflatedBytes = resolveMaxArchiveExtractBytes(maxFileSize);
        long maxEntryBytes = resolveMaxZipEntryBytes(maxFileSize);
        long totalExtractedBytes = 0L;
        try (Stream<Path> paths = Files.walk(extractedRoot)) {
            List<Path> allPaths = paths
                    .filter(path -> !path.equals(extractedRoot))
                    .sorted(Comparator.comparing(path -> toArchiveRelativePath(extractedRoot, path)))
                    .toList();
            for (Path path : allPaths) {
                if (Files.isSymbolicLink(path)) {
                    throw invalidArchiveContent();
                }
                boolean directory = Files.isDirectory(path);
                boolean file = Files.isRegularFile(path);
                if (!directory && !file) {
                    throw invalidArchiveContent();
                }
                String relativePath = toArchiveRelativePath(extractedRoot, path);
                if (!StringUtils.hasText(relativePath)) {
                    continue;
                }
                if (entries.size() + 1 > MAX_ZIP_ENTRY_COUNT) {
                    throw invalidArchiveContent();
                }
                long size = 0L;
                String contentType = "inode/directory";
                if (file) {
                    size = Files.size(path);
                    if (size > maxEntryBytes) {
                        throw invalidArchiveContent();
                    }
                    totalExtractedBytes += size;
                    if (totalExtractedBytes > maxInflatedBytes) {
                        throw invalidArchiveContent();
                    }
                    contentType = WorkspaceContentTypeResolver.guessContentType(relativePath);
                }
                entries.add(new ExtractedArchiveEntry(relativePath, directory, size, contentType));
            }
        }
        return new ExtractedArchiveTree(entries, detectCommonRootDirectoryNameFromCandidates(entries));
    }

    private String toArchiveRelativePath(Path extractedRoot, Path path) {
        Path relativePath = extractedRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        StringBuilder builder = new StringBuilder();
        for (Path segment : relativePath) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(segment.toString());
        }
        return normalizeZipCompatibleEntryPath(builder.toString());
    }

    private ArchiveExtractionPlan buildArchiveExtractionPlan(String commonRootDirectoryName,
                                                             List<ExtractedArchiveEntry> entries,
                                                             String outputPath,
                                                             String outputDirectoryName) {
        List<ArchiveItemMetadata> items = entries.stream()
                .map(entry -> toArchiveItemMetadata(entry, commonRootDirectoryName))
                .filter(item -> StringUtils.hasText(item.path()))
                .toList();
        if (items.isEmpty()) {
            throw new IllegalStateException("extract task archive is empty");
        }

        String normalizedOutputPath = workspaceNodeRulesService.normalizeDirectoryPath(outputPath);
        String normalizedOutputDirectoryName = workspaceNodeRulesService.normalizeLeafName(outputDirectoryName);
        String rootPath = joinPath(normalizedOutputPath, normalizedOutputDirectoryName);
        LinkedHashSet<String> directories = new LinkedHashSet<>();
        directories.add(rootPath);
        List<ArchiveExtractionTargetFile> files = new ArrayList<>();
        for (ArchiveItemMetadata item : items) {
            if (item.directory()) {
                directories.add(joinPath(rootPath, trimTrailingSlash(item.path())));
                continue;
            }
            String relativeParent = workspaceNodeRulesService.extractParentPath(item.path());
            String targetParent = StringUtils.hasText(relativeParent) ? joinPath(rootPath, relativeParent) : rootPath;
            collectParentDirectories(directories, rootPath, relativeParent);
            files.add(new ArchiveExtractionTargetFile(
                    item.archiveRelativePath(),
                    targetParent,
                    workspaceNodeRulesService.extractLeafName(item.path()),
                    item.contentType(),
                    item.size()
            ));
        }
        return new ArchiveExtractionPlan(List.copyOf(directories), List.copyOf(files), rootPath);
    }

    private ArchiveItemMetadata toArchiveItemMetadata(ExtractedArchiveEntry entry, String commonRootDirectoryName) {
        String path = stripCommonRootDirectory(entry.relativePath(), commonRootDirectoryName);
        return new ArchiveItemMetadata(
                entry.relativePath(),
                path,
                entry.directory(),
                entry.size(),
                entry.contentType()
        );
    }

    private Path resolveArchiveEntryFile(Path extractedRoot, String relativePath) throws IOException {
        Path normalizedRoot = extractedRoot.toAbsolutePath().normalize();
        Path current = normalizedRoot;
        for (String segment : relativePath.split("/")) {
            current = current.resolve(segment);
        }
        Path normalizedPath = current.normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw invalidArchiveContent();
        }
        return normalizedPath;
    }

    private byte[] readFileContent(Path filePath, long maxFileSize) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            copyStreamWithLimits(
                    inputStream,
                    outputStream,
                    resolveMaxZipEntryBytes(maxFileSize),
                    resolveMaxArchiveExtractBytes(maxFileSize),
                    0L
            );
            return outputStream.toByteArray();
        }
    }

    private long copyStreamWithLimits(InputStream inputStream,
                                      OutputStream outputStream,
                                      long maxEntryBytes,
                                      long maxTotalBytes,
                                      long currentTotalBytes) throws IOException {
        byte[] buffer = new byte[ZIP_READ_BUFFER_SIZE];
        long copiedBytes = 0L;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            copiedBytes += read;
            if (copiedBytes > maxEntryBytes || currentTotalBytes + copiedBytes > maxTotalBytes) {
                throw invalidArchiveContent();
            }
            outputStream.write(buffer, 0, read);
        }
        return copiedBytes;
    }

    private long resolveMaxArchiveExtractBytes(long maxFileSize) {
        long configuredLimit = maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_ZIP_EXTRACT_BYTES;
        return Math.max(1L, configuredLimit);
    }

    private String deriveSingleFileEntryName(String filename, ArchiveFormat format) {
        String safeFilename = StringUtils.hasText(filename) ? filename.trim() : "content";
        String lower = safeFilename.toLowerCase(Locale.ROOT);
        List<String> suffixes = switch (format) {
            case GZIP -> List.of(".gz");
            case BZIP2 -> List.of(".bz2");
            case XZ -> List.of(".xz");
            default -> List.of();
        };
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix) && safeFilename.length() > suffix.length()) {
                return workspaceNodeRulesService.normalizeLeafName(
                        safeFilename.substring(0, safeFilename.length() - suffix.length())
                );
            }
        }
        int lastDot = safeFilename.lastIndexOf('.');
        if (lastDot > 0) {
            return workspaceNodeRulesService.normalizeLeafName(safeFilename.substring(0, lastDot));
        }
        return workspaceNodeRulesService.normalizeLeafName(safeFilename);
    }

    private String extractLeafName(String relativePath) {
        int slashIndex = relativePath.lastIndexOf('/');
        return slashIndex >= 0 ? relativePath.substring(slashIndex + 1) : relativePath;
    }

    private ArchiveFormat resolveArchiveFormat(StoredFile source) {
        String filename = source.getFilename() == null ? "" : source.getFilename().trim().toLowerCase(Locale.ROOT);
        String contentType = source.getContentType() == null ? "" : source.getContentType().trim().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".tar.gz") || filename.endsWith(".tgz")) {
            return ArchiveFormat.TAR_GZ;
        }
        if (filename.endsWith(".tar.bz2") || filename.endsWith(".tbz2") || filename.endsWith(".tbz")) {
            return ArchiveFormat.TAR_BZ2;
        }
        if (filename.endsWith(".tar.xz") || filename.endsWith(".txz")) {
            return ArchiveFormat.TAR_XZ;
        }
        if (filename.endsWith(".zip") || filename.endsWith(".jar") || filename.endsWith(".war")) {
            return ArchiveFormat.ZIP_COMPATIBLE;
        }
        if (filename.endsWith(".7z")) {
            return ArchiveFormat.SEVEN_Z;
        }
        if (filename.endsWith(".rar")) {
            return ArchiveFormat.RAR;
        }
        if (filename.endsWith(".tar")) {
            return ArchiveFormat.TAR;
        }
        if (filename.endsWith(".gz")) {
            return ArchiveFormat.GZIP;
        }
        if (filename.endsWith(".bz2")) {
            return ArchiveFormat.BZIP2;
        }
        if (filename.endsWith(".xz")) {
            return ArchiveFormat.XZ;
        }
        if (contentType.contains("zip") || contentType.contains("java-archive")) {
            return ArchiveFormat.ZIP_COMPATIBLE;
        }
        if (contentType.contains("7z")) {
            return ArchiveFormat.SEVEN_Z;
        }
        if (contentType.contains("rar")) {
            return ArchiveFormat.RAR;
        }
        if (contentType.contains("tar")) {
            return ArchiveFormat.TAR;
        }
        if (contentType.contains("gzip")) {
            return ArchiveFormat.GZIP;
        }
        if (contentType.contains("bzip2")) {
            return ArchiveFormat.BZIP2;
        }
        if (contentType.contains("xz")) {
            return ArchiveFormat.XZ;
        }
        throw archiveReadFailed();
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
        return buildDirectoryExtractionPlan(archive.commonRootDirectoryName(), items, normalizedOutputPath, normalizedOutputDirectoryName);
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
        return readZipCompatibleArchiveMetadata(source, maxFileSize, ZIP_PRIMARY_CHARSET, true);
    }

    private WorkspaceZipArchive readZipCompatibleArchive(StoredFile source,
                                                         long maxFileSize,
                                                         Charset charset,
                                                         boolean allowCharsetFallback) {
        long compressedSize = source.getSize() == null ? 0L : Math.max(0L, source.getSize());
        byte[] signature = new byte[4];
        try (BufferedInputStream bufferedStream = new BufferedInputStream(
                requireZipCompatibleArchiveStream(source, ARCHIVE_READ_FAILED_MESSAGE))) {
            bufferedStream.mark(signature.length);
            int signatureBytes = bufferedStream.read(signature, 0, signature.length);
            bufferedStream.reset();
            try (ZipInputStream zipInputStream = new ZipInputStream(bufferedStream, charset)) {
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
        } catch (IllegalArgumentException ex) {
            if (allowCharsetFallback && shouldRetryZipWithFallbackCharset(charset, ex)) {
                return readZipCompatibleArchive(source, maxFileSize, ZIP_FALLBACK_CHARSET, false);
            }
            throw archiveReadFailed();
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw archiveReadFailed();
        }
    }

    private ZipArchiveMetadata readZipCompatibleArchiveMetadata(StoredFile source,
                                                                long maxFileSize,
                                                                Charset charset,
                                                                boolean allowCharsetFallback) {
        byte[] signature = new byte[4];
        long compressedSize = getRequiredBlob(source).size();
        try (BufferedInputStream bufferedStream = new BufferedInputStream(
                requireZipCompatibleArchiveStream(source, ARCHIVE_READ_FAILED_MESSAGE))) {
            bufferedStream.mark(signature.length);
            int signatureBytes = bufferedStream.read(signature, 0, signature.length);
            bufferedStream.reset();
            try (ZipInputStream zipInputStream = new ZipInputStream(bufferedStream, charset)) {
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
        } catch (IllegalArgumentException ex) {
            if (allowCharsetFallback && shouldRetryZipWithFallbackCharset(charset, ex)) {
                return readZipCompatibleArchiveMetadata(source, maxFileSize, ZIP_FALLBACK_CHARSET, false);
            }
            throw archiveReadFailed();
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw archiveReadFailed();
        }
    }

    private boolean shouldRetryZipWithFallbackCharset(Charset charset, IllegalArgumentException ex) {
        return ZIP_PRIMARY_CHARSET.equals(charset)
                && ex.getCause() instanceof java.nio.charset.MalformedInputException;
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
                readBlobBytes(file)
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

    private WorkspaceDownloadResult downloadZipCompatibleArchiveEntry(StoredFile source,
                                                                      String normalizedEntryPath,
                                                                      long maxFileSize) {
        return downloadZipCompatibleArchiveEntry(source, normalizedEntryPath, maxFileSize, ZIP_PRIMARY_CHARSET, true);
    }

    private WorkspaceDownloadResult downloadZipCompatibleArchiveEntry(StoredFile source,
                                                                      String normalizedEntryPath,
                                                                      long maxFileSize,
                                                                      Charset charset,
                                                                      boolean allowCharsetFallback) {
        long compressedSize = source.getSize() == null ? 0L : Math.max(0L, source.getSize());
        try (BufferedInputStream bufferedStream = new BufferedInputStream(
                requireZipCompatibleArchiveStream(source, ARCHIVE_READ_FAILED_MESSAGE));
             ZipInputStream zipInputStream = new ZipInputStream(bufferedStream, charset)) {
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
                    if (directory) {
                        if (relativePath.equals(normalizedEntryPath)) {
                            throw invalidArchiveEntry();
                        }
                    } else if (relativePath.equals(normalizedEntryPath)) {
                        byte[] content = readZipEntryContent(
                                zipInputStream,
                                maxInflatedBytes,
                                maxEntryBytes,
                                totalUncompressedBytes
                        );
                        return WorkspaceDownloadResult.inline(
                                extractLeafName(normalizedEntryPath),
                                WorkspaceContentTypeResolver.guessContentType(normalizedEntryPath),
                                content
                        );
                    } else {
                        totalUncompressedBytes += readZipEntryBytes(
                                zipInputStream,
                                maxInflatedBytes,
                                maxEntryBytes,
                                totalUncompressedBytes,
                                null
                        );
                    }
                }
                entry = zipInputStream.getNextEntry();
            }
            throw invalidArchiveEntry();
        } catch (IllegalArgumentException ex) {
            if (allowCharsetFallback && shouldRetryZipWithFallbackCharset(charset, ex)) {
                return downloadZipCompatibleArchiveEntry(source, normalizedEntryPath, maxFileSize, ZIP_FALLBACK_CHARSET, false);
            }
            throw archiveReadFailed();
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw archiveReadFailed();
        }
    }

    private boolean isMissingArchiveTool(IOException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("no such file");
    }

    private BusinessException rarToolMissing() {
        return new BusinessException(ErrorCode.ARCHIVE_READ_FAILED, RAR_TOOL_MISSING_MESSAGE);
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
        try {
            ContentBlobReadResult result = contentBlobReadApi.readBlob(source.getBlobId(), source.isDirectory());
            return result.content();
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCode.ARCHIVE_READ_FAILED, failureMessage);
        }
    }

    private byte[] readBlobBytes(StoredFile file) {
        try (InputStream inputStream = contentBlobReadApi.readBlob(file.getBlobId(), file.isDirectory()).content()) {
            return inputStream.readAllBytes();
        } catch (IOException ex) {
            throw archiveReadFailed();
        }
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

    private BusinessException invalidArchiveEntry() {
        return new BusinessException(ErrorCode.INVALID_INPUT, ARCHIVE_ENTRY_INVALID_MESSAGE);
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

    private record ExtractedArchiveEntry(String relativePath,
                                         boolean directory,
                                         long size,
                                         String contentType) implements ZipRootCandidateView {
    }

    private record ExtractedArchiveTree(List<ExtractedArchiveEntry> entries,
                                        String commonRootDirectoryName) {
    }

    private record ArchiveItemMetadata(String archiveRelativePath,
                                       String path,
                                       boolean directory,
                                       long size,
                                       String contentType) {
    }

    private record ArchiveExtractionTargetFile(String archiveRelativePath,
                                               String targetPath,
                                               String filename,
                                               String contentType,
                                               long size) {
    }

    private record ArchiveExtractionPlan(List<String> directories,
                                         List<ArchiveExtractionTargetFile> files,
                                         String extractedPath) {
    }

    @FunctionalInterface
    private interface ExtractedArchiveCallback<T> {
        T apply(Path extractedRoot) throws IOException;
    }

    private enum ArchiveFormat {
        ZIP_COMPATIBLE(".zip"),
        TAR(".tar"),
        TAR_GZ(".tar.gz"),
        TAR_BZ2(".tar.bz2"),
        TAR_XZ(".tar.xz"),
        GZIP(".gz"),
        BZIP2(".bz2"),
        XZ(".xz"),
        SEVEN_Z(".7z"),
        RAR(".rar");

        private final String tempFileSuffix;

        ArchiveFormat(String tempFileSuffix) {
            this.tempFileSuffix = tempFileSuffix;
        }

        private String tempFileSuffix() {
            return tempFileSuffix;
        }
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

    private void deleteQuietly(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            List<Path> allPaths = paths.sorted(Comparator.reverseOrder()).toList();
            IOException firstFailure = null;
            for (Path candidate : allPaths) {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ex) {
                    if (firstFailure == null) {
                        firstFailure = ex;
                    }
                }
            }
            if (firstFailure != null) {
                throw firstFailure;
            }
        }
    }
}
