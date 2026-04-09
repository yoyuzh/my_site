package com.yoyuzh.files.core;

import com.yoyuzh.admin.AdminMetricsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.events.FileEventService;
import com.yoyuzh.files.events.FileEventType;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.share.CreateFileShareLinkResponse;
import com.yoyuzh.files.share.FileShareDetailsResponse;
import com.yoyuzh.files.share.FileShareLink;
import com.yoyuzh.files.share.FileShareLinkRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.PreparedUpload;
import com.yoyuzh.files.upload.CompleteUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {
    private static final List<String> DEFAULT_DIRECTORIES = List.of("下载", "文档", "图片");
    private static final String RECYCLE_BIN_PATH_PREFIX = "/.recycle";
    private static final long RECYCLE_BIN_RETENTION_DAYS = 10L;

    private final StoredFileRepository storedFileRepository;
    private final FileBlobRepository fileBlobRepository;
    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;
    private final FileContentStorage fileContentStorage;
    private final FileShareLinkRepository fileShareLinkRepository;
    private final AdminMetricsService adminMetricsService;
    private final StoragePolicyService storagePolicyService;
    private final long maxFileSize;
    private final String packageDownloadBaseUrl;
    private final String packageDownloadSecret;
    private final long packageDownloadTtlSeconds;
    private final Clock clock;
    @Autowired(required = false)
    private FileEventService fileEventService;

    @Autowired
    public FileService(StoredFileRepository storedFileRepository,
                       FileBlobRepository fileBlobRepository,
                       FileEntityRepository fileEntityRepository,
                       StoredFileEntityRepository storedFileEntityRepository,
                       FileContentStorage fileContentStorage,
                       FileShareLinkRepository fileShareLinkRepository,
                       AdminMetricsService adminMetricsService,
                       StoragePolicyService storagePolicyService,
                       FileStorageProperties properties) {
        this(storedFileRepository, fileBlobRepository, fileEntityRepository, storedFileEntityRepository, fileContentStorage, fileShareLinkRepository, adminMetricsService, storagePolicyService, properties, Clock.systemUTC());
    }

    FileService(StoredFileRepository storedFileRepository,
                FileBlobRepository fileBlobRepository,
                FileEntityRepository fileEntityRepository,
                StoredFileEntityRepository storedFileEntityRepository,
                FileContentStorage fileContentStorage,
                FileShareLinkRepository fileShareLinkRepository,
                AdminMetricsService adminMetricsService,
                StoragePolicyService storagePolicyService,
                FileStorageProperties properties,
                Clock clock) {
        this.storedFileRepository = storedFileRepository;
        this.fileBlobRepository = fileBlobRepository;
        this.fileEntityRepository = fileEntityRepository;
        this.storedFileEntityRepository = storedFileEntityRepository;
        this.fileContentStorage = fileContentStorage;
        this.fileShareLinkRepository = fileShareLinkRepository;
        this.adminMetricsService = adminMetricsService;
        this.storagePolicyService = storagePolicyService;
        this.maxFileSize = properties.getMaxFileSize();
        this.packageDownloadBaseUrl = StringUtils.hasText(properties.getS3().getPackageDownloadBaseUrl())
                ? properties.getS3().getPackageDownloadBaseUrl().trim()
                : null;
        this.packageDownloadSecret = StringUtils.hasText(properties.getS3().getPackageDownloadSecret())
                ? properties.getS3().getPackageDownloadSecret().trim()
                : null;
        this.packageDownloadTtlSeconds = Math.max(1, properties.getS3().getPackageDownloadTtlSeconds());
        this.clock = clock;
    }

    FileService(StoredFileRepository storedFileRepository,
                FileBlobRepository fileBlobRepository,
                FileContentStorage fileContentStorage,
                FileShareLinkRepository fileShareLinkRepository,
                AdminMetricsService adminMetricsService,
                FileStorageProperties properties) {
        this(storedFileRepository, fileBlobRepository, null, null, fileContentStorage, fileShareLinkRepository, adminMetricsService, null, properties, Clock.systemUTC());
    }

    FileService(StoredFileRepository storedFileRepository,
                FileBlobRepository fileBlobRepository,
                FileContentStorage fileContentStorage,
                FileShareLinkRepository fileShareLinkRepository,
                AdminMetricsService adminMetricsService,
                FileStorageProperties properties,
                Clock clock) {
        this(storedFileRepository, fileBlobRepository, null, null, fileContentStorage, fileShareLinkRepository, adminMetricsService, null, properties, clock);
    }

    @Transactional
    public FileMetadataResponse upload(User user, String path, MultipartFile multipartFile) {
        String normalizedPath = normalizeDirectoryPath(path);
        String filename = normalizeUploadFilename(multipartFile.getOriginalFilename());
        validateUpload(user, normalizedPath, filename, multipartFile.getSize());
        ensureDirectoryHierarchy(user, normalizedPath);

        String objectKey = createBlobObjectKey();
        return executeAfterBlobStored(objectKey, () -> {
            fileContentStorage.uploadBlob(objectKey, multipartFile);
            FileBlob blob = createAndSaveBlob(objectKey, multipartFile.getContentType(), multipartFile.getSize());
            return saveFileMetadata(user, normalizedPath, filename, multipartFile.getContentType(), multipartFile.getSize(), blob);
        });
    }

    public InitiateUploadResponse initiateUpload(User user, InitiateUploadRequest request) {
        String normalizedPath = normalizeDirectoryPath(request.path());
        String filename = normalizeLeafName(request.filename());
        validateUpload(user, normalizedPath, filename, request.size());

        String objectKey = createBlobObjectKey();
        PreparedUpload preparedUpload = fileContentStorage.prepareBlobUpload(
                normalizedPath,
                filename,
                objectKey,
                request.contentType(),
                request.size()
        );

        return new InitiateUploadResponse(
                preparedUpload.direct(),
                preparedUpload.uploadUrl(),
                preparedUpload.method(),
                preparedUpload.headers(),
                preparedUpload.storageName()
        );
    }

    @Transactional
    public FileMetadataResponse completeUpload(User user, CompleteUploadRequest request) {
        String normalizedPath = normalizeDirectoryPath(request.path());
        String filename = normalizeLeafName(request.filename());
        String objectKey = normalizeBlobObjectKey(request.storageName());
        validateUpload(user, normalizedPath, filename, request.size());
        ensureDirectoryHierarchy(user, normalizedPath);

        return executeAfterBlobStored(objectKey, () -> {
            fileContentStorage.completeBlobUpload(objectKey, request.contentType(), request.size());
            FileBlob blob = createAndSaveBlob(objectKey, request.contentType(), request.size());
            return saveFileMetadata(user, normalizedPath, filename, request.contentType(), request.size(), blob);
        });
    }

    @Transactional
    public FileMetadataResponse mkdir(User user, String path) {
        String normalizedPath = normalizeDirectoryPath(path);
        if ("/".equals(normalizedPath)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "根目录无需创建");
        }
        String parentPath = extractParentPath(normalizedPath);
        String directoryName = extractLeafName(normalizedPath);
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), parentPath, directoryName)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录已存在");
        }

        fileContentStorage.createDirectory(user.getId(), normalizedPath);

        StoredFile storedFile = new StoredFile();
        storedFile.setUser(user);
        storedFile.setFilename(directoryName);
        storedFile.setPath(parentPath);
        storedFile.setContentType("directory");
        storedFile.setSize(0L);
        storedFile.setDirectory(true);
        return toResponse(storedFileRepository.save(storedFile));
    }

    public PageResponse<FileMetadataResponse> list(User user, String path, int page, int size) {
        String normalizedPath = normalizeDirectoryPath(path);
        Page<StoredFile> result = storedFileRepository.findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(
                user.getId(), normalizedPath, PageRequest.of(page, size));
        List<FileMetadataResponse> items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public List<FileMetadataResponse> recent(User user) {
        return storedFileRepository.findTop12ByUserIdAndDirectoryFalseAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PageResponse<RecycleBinItemResponse> listRecycleBin(User user, int page, int size) {
        Page<StoredFile> result = storedFileRepository.findRecycleBinRootsByUserId(user.getId(), PageRequest.of(page, size));
        List<RecycleBinItemResponse> items = result.getContent().stream().map(this::toRecycleBinResponse).toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    @Transactional
    public void ensureDefaultDirectories(User user) {
        for (String directoryName : DEFAULT_DIRECTORIES) {
            if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), "/", directoryName)) {
                continue;
            }

            String logicalPath = "/" + directoryName;
            fileContentStorage.ensureDirectory(user.getId(), logicalPath);

            StoredFile storedFile = new StoredFile();
            storedFile.setUser(user);
            storedFile.setFilename(directoryName);
            storedFile.setPath("/");
            storedFile.setContentType("directory");
            storedFile.setSize(0L);
            storedFile.setDirectory(true);
            storedFileRepository.save(storedFile);
        }
    }

    @Transactional
    public void delete(User user, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "删除");
        String fromPath = buildLogicalPath(storedFile);
        List<StoredFile> filesToRecycle = new ArrayList<>();
        filesToRecycle.add(storedFile);
        if (storedFile.isDirectory()) {
            String logicalPath = buildLogicalPath(storedFile);
            List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), logicalPath);
            filesToRecycle.addAll(descendants);
        }
        moveToRecycleBin(filesToRecycle, storedFile.getId());
        recordFileEvent(user, FileEventType.DELETED, storedFile, fromPath, buildLogicalPath(storedFile));
    }

    @Transactional
    public FileMetadataResponse restoreFromRecycleBin(User user, Long fileId) {
        StoredFile recycleRoot = getOwnedRecycleRootFile(user, fileId);
        String fromPath = buildLogicalPath(recycleRoot);
        String toPath = buildTargetLogicalPath(requireRecycleOriginalPath(recycleRoot), recycleRoot.getFilename());
        List<StoredFile> recycleGroupItems = loadRecycleGroupItems(recycleRoot);
        long additionalBytes = recycleGroupItems.stream()
                .filter(item -> !item.isDirectory())
                .mapToLong(StoredFile::getSize)
                .sum();
        ensureWithinStorageQuota(user, additionalBytes);
        validateRecycleRestoreTargets(user.getId(), recycleGroupItems);
        ensureRecycleRestoreParentHierarchy(user, recycleRoot);

        for (StoredFile item : recycleGroupItems) {
            item.setPath(requireRecycleOriginalPath(item));
            item.setDeletedAt(null);
            item.setRecycleOriginalPath(null);
            item.setRecycleGroupId(null);
            item.setRecycleRoot(false);
        }
        storedFileRepository.saveAll(recycleGroupItems);
        recordFileEvent(user, FileEventType.RESTORED, recycleRoot, fromPath, toPath);
        return toResponse(recycleRoot);
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    @Transactional
    public void pruneExpiredRecycleBinItems() {
        List<StoredFile> expiredItems = storedFileRepository.findByDeletedAtBefore(LocalDateTime.now().minusDays(RECYCLE_BIN_RETENTION_DAYS));
        if (expiredItems.isEmpty()) {
            return;
        }

        List<FileBlob> blobsToDelete = collectBlobsToDelete(
                expiredItems.stream().filter(item -> !item.isDirectory()).toList()
        );
        storedFileRepository.deleteAll(expiredItems);
        deleteBlobs(blobsToDelete);
    }

    @Transactional
    public FileMetadataResponse rename(User user, Long fileId, String nextFilename) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "重命名");
        String fromPath = buildLogicalPath(storedFile);
        String sanitizedFilename = normalizeLeafName(nextFilename);
        if (sanitizedFilename.equals(storedFile.getFilename())) {
            return toResponse(storedFile);
        }
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), storedFile.getPath(), sanitizedFilename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "同目录下文件已存在");
        }

        if (storedFile.isDirectory()) {
            String oldLogicalPath = buildLogicalPath(storedFile);
            String newLogicalPath = "/".equals(storedFile.getPath())
                    ? "/" + sanitizedFilename
                    : storedFile.getPath() + "/" + sanitizedFilename;

            List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), oldLogicalPath);
            for (StoredFile descendant : descendants) {
                if (descendant.getPath().equals(oldLogicalPath)) {
                    descendant.setPath(newLogicalPath);
                    continue;
                }

                descendant.setPath(newLogicalPath + descendant.getPath().substring(oldLogicalPath.length()));
            }
            if (!descendants.isEmpty()) {
                storedFileRepository.saveAll(descendants);
            }
        }

        storedFile.setFilename(sanitizedFilename);
        FileMetadataResponse response = toResponse(storedFileRepository.save(storedFile));
        recordFileEvent(user, FileEventType.RENAMED, storedFile, fromPath, buildLogicalPath(storedFile));
        return response;
    }

    @Transactional
    public FileMetadataResponse move(User user, Long fileId, String nextPath) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "移动");
        String fromPath = buildLogicalPath(storedFile);
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        if (normalizedTargetPath.equals(storedFile.getPath())) {
            return toResponse(storedFile);
        }

        ensureExistingDirectoryPath(user.getId(), normalizedTargetPath);
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), normalizedTargetPath, storedFile.getFilename())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目标目录已存在同名文件");
        }

        if (storedFile.isDirectory()) {
            String oldLogicalPath = buildLogicalPath(storedFile);
            String newLogicalPath = "/".equals(normalizedTargetPath)
                    ? "/" + storedFile.getFilename()
                    : normalizedTargetPath + "/" + storedFile.getFilename();
            if (newLogicalPath.equals(oldLogicalPath) || newLogicalPath.startsWith(oldLogicalPath + "/")) {
                throw new BusinessException(ErrorCode.UNKNOWN, "不能移动到当前目录或其子目录");
            }

            List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), oldLogicalPath);
            for (StoredFile descendant : descendants) {
                if (descendant.getPath().equals(oldLogicalPath)) {
                    descendant.setPath(newLogicalPath);
                    continue;
                }

                descendant.setPath(newLogicalPath + descendant.getPath().substring(oldLogicalPath.length()));
            }
            if (!descendants.isEmpty()) {
                storedFileRepository.saveAll(descendants);
            }
        }

        storedFile.setPath(normalizedTargetPath);
        FileMetadataResponse response = toResponse(storedFileRepository.save(storedFile));
        recordFileEvent(user, FileEventType.MOVED, storedFile, fromPath, buildLogicalPath(storedFile));
        return response;
    }

    @Transactional
    public FileMetadataResponse copy(User user, Long fileId, String nextPath) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "复制");
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        ensureExistingDirectoryPath(user.getId(), normalizedTargetPath);
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), normalizedTargetPath, storedFile.getFilename())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目标目录已存在同名文件");
        }

        if (!storedFile.isDirectory()) {
            ensureWithinStorageQuota(user, storedFile.getSize());
            return toResponse(saveCopiedStoredFile(copyStoredFile(storedFile, user, normalizedTargetPath), user));
        }

        String oldLogicalPath = buildLogicalPath(storedFile);
        String newLogicalPath = buildTargetLogicalPath(normalizedTargetPath, storedFile.getFilename());
        if (newLogicalPath.equals(oldLogicalPath) || newLogicalPath.startsWith(oldLogicalPath + "/")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "不能复制到当前目录或其子目录");
        }

        List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), oldLogicalPath);
        long additionalBytes = descendants.stream()
                .filter(descendant -> !descendant.isDirectory())
                .mapToLong(StoredFile::getSize)
                .sum();
        ensureWithinStorageQuota(user, additionalBytes);
        List<StoredFile> copiedEntries = new ArrayList<>();

        StoredFile copiedRoot = copyStoredFile(storedFile, user, normalizedTargetPath);
        copiedEntries.add(copiedRoot);

        descendants.stream()
                .sorted(Comparator
                        .comparingInt((StoredFile descendant) -> descendant.getPath().length())
                        .thenComparing(descendant -> descendant.isDirectory() ? 0 : 1)
                        .thenComparing(StoredFile::getFilename))
                .forEach(descendant -> {
                    String copiedPath = remapCopiedPath(descendant.getPath(), oldLogicalPath, newLogicalPath);
                    if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), copiedPath, descendant.getFilename())) {
                        throw new BusinessException(ErrorCode.UNKNOWN, "目标目录已存在同名文件");
                    }

                    copiedEntries.add(copyStoredFile(descendant, user, copiedPath));
                });

        StoredFile savedRoot = null;
        for (StoredFile copiedEntry : copiedEntries) {
            StoredFile savedEntry = saveCopiedStoredFile(copiedEntry, user);
            if (savedRoot == null) {
                savedRoot = savedEntry;
            }
        }
        return toResponse(savedRoot == null ? copiedRoot : savedRoot);
    }

    public ResponseEntity<?> download(User user, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "下载");
        if (storedFile.isDirectory()) {
            return downloadDirectory(user, storedFile);
        }

        if (shouldUsePublicPackageDownload(storedFile)) {
            return ResponseEntity.status(302)
                    .location(URI.create(buildPublicPackageDownloadUrl(storedFile)))
                    .build();
        }

        if (fileContentStorage.supportsDirectDownload()) {
            return ResponseEntity.status(302)
                    .location(URI.create(fileContentStorage.createBlobDownloadUrl(
                            getRequiredBlob(storedFile).getObjectKey(),
                            storedFile.getFilename())))
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(storedFile.getFilename(), StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        storedFile.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : storedFile.getContentType()))
                .body(fileContentStorage.readBlob(getRequiredBlob(storedFile).getObjectKey()));
    }

    public DownloadUrlResponse getDownloadUrl(User user, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "下载");
        if (storedFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录不支持下载");
        }
        adminMetricsService.recordDownloadTraffic(storedFile.getSize());

        if (shouldUsePublicPackageDownload(storedFile)) {
            return new DownloadUrlResponse(buildPublicPackageDownloadUrl(storedFile));
        }

        if (fileContentStorage.supportsDirectDownload()) {
            return new DownloadUrlResponse(fileContentStorage.createBlobDownloadUrl(
                    getRequiredBlob(storedFile).getObjectKey(),
                    storedFile.getFilename()
            ));
        }

        return new DownloadUrlResponse("/api/files/download/" + storedFile.getId());
    }

    @Transactional
    public CreateFileShareLinkResponse createShareLink(User user, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "分享");
        if (storedFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录暂不支持分享链接");
        }

        FileShareLink shareLink = new FileShareLink();
        shareLink.setOwner(user);
        shareLink.setFile(storedFile);
        shareLink.setToken(UUID.randomUUID().toString().replace("-", ""));
        FileShareLink saved = fileShareLinkRepository.save(shareLink);

        return new CreateFileShareLinkResponse(
                saved.getToken(),
                storedFile.getFilename(),
                storedFile.getSize(),
                storedFile.getContentType(),
                saved.getCreatedAt()
        );
    }

    public FileShareDetailsResponse getShareDetails(String token) {
        FileShareLink shareLink = getShareLink(token);
        StoredFile storedFile = shareLink.getFile();
        return new FileShareDetailsResponse(
                shareLink.getToken(),
                shareLink.getOwner().getUsername(),
                storedFile.getFilename(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                shareLink.getCreatedAt()
        );
    }

    @Transactional
    public FileMetadataResponse importSharedFile(User recipient, String token, String path) {
        FileShareLink shareLink = getShareLink(token);
        StoredFile sourceFile = shareLink.getFile();
        if (sourceFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录暂不支持导入");
        }
        return importReferencedBlob(
                recipient,
                path,
                sourceFile.getFilename(),
                sourceFile.getContentType(),
                sourceFile.getSize(),
                getRequiredBlob(sourceFile)
        );
    }

    @Transactional
    public FileMetadataResponse importExternalFile(User recipient,
                                                   String path,
                                                   String filename,
                                                   String contentType,
                                                   long size,
                                                   byte[] content) {
        String normalizedPath = normalizeDirectoryPath(path);
        String normalizedFilename = normalizeLeafName(filename);
        validateUpload(recipient, normalizedPath, normalizedFilename, size);
        ensureDirectoryHierarchy(recipient, normalizedPath);
        String objectKey = createBlobObjectKey();
        return executeAfterBlobStored(objectKey, () -> {
            fileContentStorage.storeBlob(objectKey, contentType, content);
            FileBlob blob = createAndSaveBlob(objectKey, contentType, size);

            return saveFileMetadata(
                    recipient,
                    normalizedPath,
                    normalizedFilename,
                    contentType,
                    size,
                    blob
            );
        });
    }

    @Transactional
    public void importExternalFilesAtomically(User recipient,
                                              List<String> directories,
                                              List<ExternalFileImport> files) {
        importExternalFilesAtomically(recipient, directories, files, null);
    }

    @Transactional
    public void importExternalFilesAtomically(User recipient,
                                              List<String> directories,
                                              List<ExternalFileImport> files,
                                              ExternalImportProgressListener progressListener) {
        List<String> normalizedDirectories = normalizeExternalImportDirectories(directories);
        List<ExternalFileImport> normalizedFiles = normalizeExternalImportFiles(files);
        validateExternalImportBatch(recipient, normalizedDirectories, normalizedFiles);

        List<String> writtenBlobObjectKeys = new ArrayList<>();
        int totalDirectoryCount = normalizedDirectories.size();
        int totalFileCount = normalizedFiles.size();
        int processedDirectoryCount = 0;
        int processedFileCount = 0;
        try {
            for (String directory : normalizedDirectories) {
                mkdir(recipient, directory);
                processedDirectoryCount += 1;
                reportExternalImportProgress(progressListener, processedFileCount, totalFileCount,
                        processedDirectoryCount, totalDirectoryCount);
            }
            for (ExternalFileImport file : normalizedFiles) {
                storeExternalImportFile(recipient, file, writtenBlobObjectKeys);
                processedFileCount += 1;
                reportExternalImportProgress(progressListener, processedFileCount, totalFileCount,
                        processedDirectoryCount, totalDirectoryCount);
            }
        } catch (RuntimeException ex) {
            cleanupWrittenBlobs(writtenBlobObjectKeys, ex);
            throw ex;
        }
    }

    private ResponseEntity<byte[]> downloadDirectory(User user, StoredFile directory) {
        String archiveName = directory.getFilename() + ".zip";
        byte[] archiveBytes = buildArchiveBytes(directory);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(archiveName, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(archiveBytes);
    }

    public byte[] buildArchiveBytes(StoredFile source) {
        return buildArchiveBytes(source, null);
    }

    public byte[] buildArchiveBytes(StoredFile source, ArchiveBuildProgressListener progressListener) {
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
            throw new BusinessException(ErrorCode.UNKNOWN, "目录压缩失败");
        }
    }

    public ZipCompatibleArchive readZipCompatibleArchive(StoredFile source) {
        byte[] archiveBytes = fileContentStorage.readBlob(getRequiredBlob(source).getObjectKey());
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new ByteArrayInputStream(archiveBytes),
                StandardCharsets.UTF_8)) {
            List<ZipCompatibleArchiveEntry> entries = new ArrayList<>();
            Map<String, Boolean> seenEntries = new HashMap<>();
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                String relativePath = normalizeZipCompatibleEntryPath(entry.getName());
                if (StringUtils.hasText(relativePath)) {
                    boolean directory = entry.isDirectory() || entry.getName().endsWith("/");
                    Boolean existingType = seenEntries.putIfAbsent(relativePath, directory);
                    if (existingType != null) {
                        throw new BusinessException(ErrorCode.UNKNOWN, "压缩包内容不合法");
                    }
                    entries.add(new ZipCompatibleArchiveEntry(
                            relativePath,
                            directory,
                            directory ? new byte[0] : zipInputStream.readAllBytes()
                    ));
                }
                entry = zipInputStream.getNextEntry();
            }
            if (entries.isEmpty() && !hasZipCompatibleSignature(archiveBytes)) {
                throw new BusinessException(ErrorCode.UNKNOWN, "压缩包读取失败");
            }
            return new ZipCompatibleArchive(entries, detectCommonRootDirectoryName(entries));
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "压缩包读取失败");
        }
    }

    private boolean shouldUsePublicPackageDownload(StoredFile storedFile) {
        return fileContentStorage.supportsDirectDownload()
                && StringUtils.hasText(packageDownloadBaseUrl)
                && StringUtils.hasText(packageDownloadSecret)
                && isAppPackage(storedFile);
    }

    private boolean isAppPackage(StoredFile storedFile) {
        String filename = storedFile.getFilename() == null ? "" : storedFile.getFilename().toLowerCase(Locale.ROOT);
        String contentType = storedFile.getContentType() == null ? "" : storedFile.getContentType().toLowerCase(Locale.ROOT);
        return filename.endsWith(".apk")
                || filename.endsWith(".ipa")
                || "application/vnd.android.package-archive".equals(contentType)
                || "application/octet-stream".equals(contentType) && (filename.endsWith(".apk") || filename.endsWith(".ipa"));
    }

    private String buildPublicPackageDownloadUrl(StoredFile storedFile) {
        FileBlob blob = getRequiredBlob(storedFile);
        String base = packageDownloadBaseUrl.endsWith("/")
                ? packageDownloadBaseUrl.substring(0, packageDownloadBaseUrl.length() - 1)
                : packageDownloadBaseUrl;
        String path = "/" + trimLeadingSlash(blob.getObjectKey());
        if (base.endsWith("/_dl")) {
            path = "/_dl" + path;
        }
        long expires = clock.instant().getEpochSecond() + packageDownloadTtlSeconds;
        String signature = buildSecureLinkSignature(path, expires);
        return base
                + "/"
                + trimLeadingSlash(blob.getObjectKey())
                + "?md5="
                + encodeQueryParam(signature)
                + "&expires="
                + expires
                + "&response-content-disposition="
                + encodeQueryParam(buildAsciiContentDisposition(storedFile.getFilename()));
    }

    private String buildAsciiContentDisposition(String filename) {
        String sanitized = sanitizeDownloadFilename(filename);
        StringBuilder disposition = new StringBuilder("attachment; filename=\"")
                .append(escapeContentDispositionFilename(buildAsciiDownloadFilename(sanitized)))
                .append("\"");
        if (StringUtils.hasText(sanitized)) {
            disposition.append("; filename*=UTF-8''")
                    .append(sanitized);
        }
        return disposition.toString();
    }

    private String buildAsciiDownloadFilename(String filename) {
        String normalized = sanitizeDownloadFilename(filename);
        if (!StringUtils.hasText(normalized)) {
            return "download";
        }

        String sanitized = normalized.replaceAll("[\\r\\n]", "_");
        StringBuilder ascii = new StringBuilder(sanitized.length());
        for (int i = 0; i < sanitized.length(); i++) {
            char current = sanitized.charAt(i);
            if (current >= 32 && current <= 126 && current != '"' && current != '\\') {
                ascii.append(current);
            } else {
                ascii.append('_');
            }
        }

        String fallback = ascii.toString().trim();
        String extension = extractAsciiExtension(normalized);
        String baseName = extension.isEmpty() ? fallback : fallback.substring(0, Math.max(0, fallback.length() - extension.length()));
        if (baseName.replace("_", "").isBlank()) {
            return extension.isEmpty() ? "download" : "download" + extension;
        }
        return fallback;
    }

    private String sanitizeDownloadFilename(String filename) {
        return StringUtils.hasText(filename) ? filename.trim().replaceAll("[\\r\\n]", "_") : "";
    }

    private String extractAsciiExtension(String filename) {
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex > 0 && extensionIndex < filename.length() - 1) {
            String extension = filename.substring(extensionIndex).replaceAll("[^A-Za-z0-9.]", "");
            return StringUtils.hasText(extension) ? extension : "";
        }
        return "";
    }

    private String escapeContentDispositionFilename(String filename) {
        return filename.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trimLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private String buildSecureLinkSignature(String path, long expires) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest((expires + path + " " + packageDownloadSecret).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("生成下载签名失败", ex);
        }
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private FileMetadataResponse saveFileMetadata(User user,
                                                  String normalizedPath,
                                                  String filename,
                                                  String contentType,
                                                  long size,
                                                  FileBlob blob) {
        StoredFile storedFile = new StoredFile();
        storedFile.setUser(user);
        storedFile.setFilename(filename);
        storedFile.setPath(normalizedPath);
        storedFile.setContentType(contentType);
        storedFile.setSize(size);
        storedFile.setDirectory(false);
        storedFile.setBlob(blob);
        FileEntity primaryEntity = createOrReferencePrimaryEntity(user, blob);
        storedFile.setPrimaryEntity(primaryEntity);
        StoredFile savedFile = storedFileRepository.save(storedFile);
        savePrimaryEntityRelation(savedFile, primaryEntity);
        recordFileEvent(user, FileEventType.CREATED, savedFile, null, buildLogicalPath(savedFile));
        return toResponse(savedFile);
    }

    private FileEntity createOrReferencePrimaryEntity(User user, FileBlob blob) {
        if (fileEntityRepository == null) {
            return createTransientPrimaryEntity(user, blob);
        }

        Optional<FileEntity> existingEntity = fileEntityRepository.findByObjectKeyAndEntityType(
                blob.getObjectKey(),
                FileEntityType.VERSION
        );
        if (existingEntity.isPresent()) {
            FileEntity entity = existingEntity.get();
            entity.setReferenceCount(entity.getReferenceCount() + 1);
            return fileEntityRepository.save(entity);
        }

        return fileEntityRepository.save(createTransientPrimaryEntity(user, blob));
    }

    private FileEntity createTransientPrimaryEntity(User user, FileBlob blob) {
        FileEntity entity = new FileEntity();
        entity.setObjectKey(blob.getObjectKey());
        entity.setContentType(blob.getContentType());
        entity.setSize(blob.getSize());
        entity.setEntityType(FileEntityType.VERSION);
        entity.setReferenceCount(1);
        entity.setCreatedBy(user);
        entity.setStoragePolicyId(resolveDefaultStoragePolicyId());
        return entity;
    }

    private Long resolveDefaultStoragePolicyId() {
        if (storagePolicyService == null) {
            return null;
        }
        return storagePolicyService.ensureDefaultPolicy().getId();
    }

    private void savePrimaryEntityRelation(StoredFile storedFile, FileEntity primaryEntity) {
        if (storedFileEntityRepository == null) {
            return;
        }

        StoredFileEntity relation = new StoredFileEntity();
        relation.setStoredFile(storedFile);
        relation.setFileEntity(primaryEntity);
        relation.setEntityRole("PRIMARY");
        storedFileEntityRepository.save(relation);
    }

    private FileShareLink getShareLink(String token) {
        return fileShareLinkRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "分享链接不存在"));
    }

    private RecycleBinItemResponse toRecycleBinResponse(StoredFile storedFile) {
        LocalDateTime deletedAt = storedFile.getDeletedAt();
        if (deletedAt == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }

        return new RecycleBinItemResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                requireRecycleOriginalPath(storedFile),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt(),
                deletedAt,
                deletedAt.plusDays(RECYCLE_BIN_RETENTION_DAYS)
        );
    }

    private StoredFile getOwnedFile(User user, Long fileId, String action) {
        StoredFile storedFile = storedFileRepository.findDetailedById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!storedFile.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限" + action + "该文件");
        }
        return storedFile;
    }

    private StoredFile getOwnedActiveFile(User user, Long fileId, String action) {
        StoredFile storedFile = getOwnedFile(user, fileId, action);
        if (storedFile.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }
        return storedFile;
    }

    private StoredFile getOwnedRecycleRootFile(User user, Long fileId) {
        StoredFile storedFile = getOwnedFile(user, fileId, "恢复");
        if (storedFile.getDeletedAt() == null || !storedFile.isRecycleRoot()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return storedFile;
    }

    private List<StoredFile> loadRecycleGroupItems(StoredFile recycleRoot) {
        List<StoredFile> items = storedFileRepository.findByRecycleGroupId(recycleRoot.getRecycleGroupId());
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return items;
    }

    private void validateUpload(User user, String normalizedPath, String filename, long size) {
        long effectiveMaxUploadSize = Math.min(maxFileSize, user.getMaxUploadSizeBytes());
        if (size > effectiveMaxUploadSize) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件大小超出限制");
        }
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), normalizedPath, filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "同目录下文件已存在");
        }
        ensureWithinStorageQuota(user, size);
    }

    private List<String> normalizeExternalImportDirectories(List<String> directories) {
        if (directories == null || directories.isEmpty()) {
            return List.of();
        }
        return directories.stream()
                .map(this::normalizeDirectoryPath)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).thenComparing(String::compareTo))
                .toList();
    }

    private List<ExternalFileImport> normalizeExternalImportFiles(List<ExternalFileImport> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(file -> new ExternalFileImport(
                        normalizeDirectoryPath(file.path()),
                        normalizeLeafName(file.filename()),
                        StringUtils.hasText(file.contentType()) ? file.contentType().trim() : "application/octet-stream",
                        file.content() == null ? new byte[0] : file.content()
                ))
                .toList();
    }

    private void validateExternalImportBatch(User recipient,
                                             List<String> directories,
                                             List<ExternalFileImport> files) {
        ensureWithinStorageQuota(recipient, files.stream().mapToLong(ExternalFileImport::size).sum());

        Set<String> plannedTargets = new LinkedHashSet<>();
        for (String directory : directories) {
            if ("/".equals(directory)) {
                continue;
            }
            if (!plannedTargets.add(directory)) {
                continue;
            }
            String parentPath = extractParentPath(directory);
            String directoryName = extractLeafName(directory);
            if (storedFileRepository.existsByUserIdAndPathAndFilename(recipient.getId(), parentPath, directoryName)) {
                throw new BusinessException(ErrorCode.UNKNOWN, "解压目标已存在");
            }
        }

        for (ExternalFileImport file : files) {
            String logicalPath = buildTargetLogicalPath(file.path(), file.filename());
            if (plannedTargets.contains(logicalPath) || !plannedTargets.add(logicalPath)) {
                throw new BusinessException(ErrorCode.UNKNOWN, "解压目标已存在");
            }
            if (storedFileRepository.existsByUserIdAndPathAndFilename(recipient.getId(), file.path(), file.filename())) {
                throw new BusinessException(ErrorCode.UNKNOWN, "同目录下文件已存在");
            }
        }
    }

    private void ensureWithinStorageQuota(User user, long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }

        long usedBytes = storedFileRepository.sumFileSizeByUserId(user.getId());
        long quotaBytes = user.getStorageQuotaBytes();
        if (usedBytes > Long.MAX_VALUE - additionalBytes || usedBytes + additionalBytes > quotaBytes) {
            throw new BusinessException(ErrorCode.UNKNOWN, "存储空间不足");
        }
    }

    private void ensureDirectoryHierarchy(User user, String normalizedPath) {
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

    private void storeExternalImportFile(User recipient,
                                         ExternalFileImport file,
                                         List<String> writtenBlobObjectKeys) {
        validateUpload(recipient, file.path(), file.filename(), file.size());
        ensureDirectoryHierarchy(recipient, file.path());
        String objectKey = createBlobObjectKey();
        writtenBlobObjectKeys.add(objectKey);
        fileContentStorage.storeBlob(objectKey, file.contentType(), file.content());
        FileBlob blob = createAndSaveBlob(objectKey, file.contentType(), file.size());
        saveFileMetadata(
                recipient,
                file.path(),
                file.filename(),
                file.contentType(),
                file.size(),
                blob
        );
    }

    private void moveToRecycleBin(List<StoredFile> filesToRecycle, Long recycleRootId) {
        if (filesToRecycle.isEmpty()) {
            return;
        }

        StoredFile recycleRoot = filesToRecycle.stream()
                .filter(item -> recycleRootId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        String recycleGroupId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime deletedAt = LocalDateTime.now();
        String rootLogicalPath = buildLogicalPath(recycleRoot);
        String recycleRootPath = buildRecycleBinPath(recycleGroupId, recycleRoot.getPath());
        String recycleRootLogicalPath = buildTargetLogicalPath(recycleRootPath, recycleRoot.getFilename());

        List<StoredFile> orderedItems = filesToRecycle.stream()
                .sorted(Comparator
                        .comparingInt((StoredFile item) -> buildLogicalPath(item).length())
                        .thenComparing(item -> item.isDirectory() ? 0 : 1)
                        .thenComparing(StoredFile::getFilename))
                .toList();

        for (StoredFile item : orderedItems) {
            String originalPath = item.getPath();
            String recyclePath = recycleRootId.equals(item.getId())
                    ? recycleRootPath
                    : remapCopiedPath(item.getPath(), rootLogicalPath, recycleRootLogicalPath);
            item.setDeletedAt(deletedAt);
            item.setRecycleOriginalPath(originalPath);
            item.setRecycleGroupId(recycleGroupId);
            item.setRecycleRoot(recycleRootId.equals(item.getId()));
            item.setPath(recyclePath);
        }

        storedFileRepository.saveAll(orderedItems);
    }

    private String buildRecycleBinPath(String recycleGroupId, String originalPath) {
        if ("/".equals(originalPath)) {
            return RECYCLE_BIN_PATH_PREFIX + "/" + recycleGroupId;
        }
        return RECYCLE_BIN_PATH_PREFIX + "/" + recycleGroupId + originalPath;
    }

    private String requireRecycleOriginalPath(StoredFile storedFile) {
        if (!StringUtils.hasText(storedFile.getRecycleOriginalPath())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return storedFile.getRecycleOriginalPath();
    }

    private void validateRecycleRestoreTargets(Long userId, List<StoredFile> recycleGroupItems) {
        for (StoredFile item : recycleGroupItems) {
            String originalPath = requireRecycleOriginalPath(item);
            if (storedFileRepository.existsByUserIdAndPathAndFilename(userId, originalPath, item.getFilename())) {
                throw new BusinessException(ErrorCode.UNKNOWN, "原目录已存在同名文件，无法恢复");
            }
        }
    }

    private void ensureRecycleRestoreParentHierarchy(User user, StoredFile recycleRoot) {
        ensureDirectoryHierarchy(user, requireRecycleOriginalPath(recycleRoot));
    }

    private void ensureExistingDirectoryPath(Long userId, String normalizedPath) {
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

    private String normalizeUploadFilename(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename);
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不能为空");
        }
        return normalizeLeafName(filename);
    }

    private FileMetadataResponse toResponse(StoredFile storedFile) {
        String logicalPath = storedFile.isDirectory() ? buildLogicalPath(storedFile) : storedFile.getPath();
        return new FileMetadataResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                logicalPath,
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt());
    }

    private String normalizeDirectoryPath(String path) {
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

    private String extractParentPath(String normalizedPath) {
        int lastSlash = normalizedPath.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : normalizedPath.substring(0, lastSlash);
    }

    private String extractLeafName(String normalizedPath) {
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }

    private String buildLogicalPath(StoredFile storedFile) {
        return "/".equals(storedFile.getPath())
                ? "/" + storedFile.getFilename()
                : storedFile.getPath() + "/" + storedFile.getFilename();
    }

    private String buildTargetLogicalPath(String normalizedTargetPath, String filename) {
        return "/".equals(normalizedTargetPath)
                ? "/" + filename
                : normalizedTargetPath + "/" + filename;
    }

    private String remapCopiedPath(String currentPath, String oldLogicalPath, String newLogicalPath) {
        if (currentPath.equals(oldLogicalPath)) {
            return newLogicalPath;
        }
        return newLogicalPath + currentPath.substring(oldLogicalPath.length());
    }

    private StoredFile copyStoredFile(StoredFile source, User owner, String nextPath) {
        StoredFile copiedFile = new StoredFile();
        copiedFile.setUser(owner);
        copiedFile.setFilename(source.getFilename());
        copiedFile.setPath(nextPath);
        copiedFile.setContentType(source.getContentType());
        copiedFile.setSize(source.getSize());
        copiedFile.setDirectory(source.isDirectory());
        copiedFile.setBlob(source.getBlob());
        return copiedFile;
    }

    private StoredFile saveCopiedStoredFile(StoredFile copiedFile, User owner) {
        if (!copiedFile.isDirectory() && copiedFile.getBlob() != null && copiedFile.getPrimaryEntity() == null) {
            copiedFile.setPrimaryEntity(createOrReferencePrimaryEntity(owner, copiedFile.getBlob()));
        }
        StoredFile savedFile = storedFileRepository.save(copiedFile);
        if (!savedFile.isDirectory() && savedFile.getPrimaryEntity() != null) {
            savePrimaryEntityRelation(savedFile, savedFile.getPrimaryEntity());
        }
        recordFileEvent(owner, FileEventType.CREATED, savedFile, null, buildLogicalPath(savedFile));
        return savedFile;
    }

    private void writeDirectoryArchiveEntries(ZipOutputStream zipOutputStream,
                                              Set<String> createdEntries,
                                              StoredFile directory,
                                              ArchiveBuildProgressState progressState) throws IOException {
        String logicalPath = buildLogicalPath(directory);
        List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(directory.getUser().getId(), logicalPath)
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
        writeFileEntry(zipOutputStream, createdEntries, entryName, progressState,
                fileContentStorage.readBlob(getRequiredBlob(file).getObjectKey()));
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
            throw new BusinessException(ErrorCode.UNKNOWN, "压缩包内容不合法");
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
                throw new BusinessException(ErrorCode.UNKNOWN, "压缩包内容不合法");
            }
            if (sanitized.length() > 0) {
                sanitized.append('/');
            }
            sanitized.append(normalizeLeafName(segment));
        }
        return sanitized.toString();
    }

    private String detectCommonRootDirectoryName(List<ZipCompatibleArchiveEntry> entries) {
        String candidate = null;
        boolean hasNestedEntry = false;
        boolean hasDirectoryCandidate = false;
        for (ZipCompatibleArchiveEntry entry : entries) {
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

    private boolean hasZipCompatibleSignature(byte[] archiveBytes) {
        if (archiveBytes == null || archiveBytes.length < 4) {
            return false;
        }
        return archiveBytes[0] == 'P'
                && archiveBytes[1] == 'K'
                && (archiveBytes[2] == 3 || archiveBytes[2] == 5 || archiveBytes[2] == 7)
                && (archiveBytes[3] == 4 || archiveBytes[3] == 6 || archiveBytes[3] == 8);
    }

    public ArchiveSourceSummary summarizeArchiveSource(StoredFile source) {
        if (!source.isDirectory()) {
            return new ArchiveSourceSummary(1, 0);
        }
        String logicalPath = buildLogicalPath(source);
        List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(source.getUser().getId(), logicalPath);
        int directoryCount = 1 + (int) descendants.stream().filter(StoredFile::isDirectory).count();
        int fileCount = (int) descendants.stream().filter(file -> !file.isDirectory()).count();
        return new ArchiveSourceSummary(fileCount, directoryCount);
    }

    private ArchiveBuildProgressState createArchiveBuildProgressState(StoredFile source,
                                                                      ArchiveBuildProgressListener progressListener) {
        if (progressListener == null) {
            return null;
        }
        ArchiveSourceSummary summary = summarizeArchiveSource(source);
        return new ArchiveBuildProgressState(progressListener, summary.fileCount(), summary.directoryCount());
    }

    private void reportArchiveProgress(ArchiveBuildProgressState progressState) {
        if (progressState == null) {
            return;
        }
        progressState.listener.onProgress(new ArchiveBuildProgress(
                progressState.processedFileCount,
                progressState.totalFileCount,
                progressState.processedDirectoryCount,
                progressState.totalDirectoryCount
        ));
    }

    private void reportExternalImportProgress(ExternalImportProgressListener progressListener,
                                              int processedFileCount,
                                              int totalFileCount,
                                              int processedDirectoryCount,
                                              int totalDirectoryCount) {
        if (progressListener == null) {
            return;
        }
        progressListener.onProgress(new ExternalImportProgress(
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
                                byte[] content)
            throws IOException {
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

    private void recordFileEvent(User user,
                                 FileEventType eventType,
                                 StoredFile storedFile,
                                 String fromPath,
                                 String toPath) {
        if (fileEventService == null || storedFile == null || storedFile.getId() == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", eventType.name());
        payload.put("fileId", storedFile.getId());
        payload.put("filename", storedFile.getFilename());
        payload.put("path", storedFile.getPath());
        payload.put("directory", storedFile.isDirectory());
        payload.put("contentType", storedFile.getContentType());
        payload.put("size", storedFile.getSize());
        if (fromPath != null) {
            payload.put("fromPath", fromPath);
        }
        if (toPath != null) {
            payload.put("toPath", toPath);
        }
        fileEventService.record(user, eventType, storedFile.getId(), fromPath, toPath, payload);
    }

    private String normalizeLeafName(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "" : filename).trim();
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不能为空");
        }
        if (cleaned.contains("/") || cleaned.contains("\\") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不合法");
        }
        return cleaned;
    }

    private String createBlobObjectKey() {
        return "blobs/" + UUID.randomUUID();
    }

    private String normalizeBlobObjectKey(String objectKey) {
        String cleaned = StringUtils.cleanPath(objectKey == null ? "" : objectKey).trim().replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.contains("..") || cleaned.startsWith("/") || !cleaned.startsWith("blobs/")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "上传对象标识不合法");
        }
        return cleaned;
    }

    private <T> T executeAfterBlobStored(String objectKey, BlobWriteOperation<T> operation) {
        try {
            return operation.run();
        } catch (RuntimeException ex) {
            try {
                fileContentStorage.deleteBlob(objectKey);
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }
    }

    private void cleanupWrittenBlobs(List<String> writtenBlobObjectKeys, RuntimeException ex) {
        for (String objectKey : writtenBlobObjectKeys) {
            try {
                fileContentStorage.deleteBlob(objectKey);
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
        }
    }

    private FileBlob createAndSaveBlob(String objectKey, String contentType, long size) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(size);
        return fileBlobRepository.save(blob);
    }

    private FileMetadataResponse importReferencedBlob(User recipient,
                                                      String path,
                                                      String filename,
                                                      String contentType,
                                                      long size,
                                                      FileBlob blob) {
        String normalizedPath = normalizeDirectoryPath(path);
        String normalizedFilename = normalizeLeafName(filename);
        validateUpload(recipient, normalizedPath, normalizedFilename, size);
        ensureDirectoryHierarchy(recipient, normalizedPath);
        return saveFileMetadata(
                recipient,
                normalizedPath,
                normalizedFilename,
                contentType,
                size,
                blob
        );
    }

    private FileBlob getRequiredBlob(StoredFile storedFile) {
        if (storedFile.isDirectory() || storedFile.getBlob() == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件内容不存在");
        }
        return storedFile.getBlob();
    }

    private List<FileBlob> collectBlobsToDelete(List<StoredFile> filesToDelete) {
        Map<Long, BlobDeletionCandidate> candidates = new HashMap<>();
        for (StoredFile file : filesToDelete) {
            if (file.getBlob() == null || file.getBlob().getId() == null) {
                continue;
            }
            BlobDeletionCandidate candidate = candidates.computeIfAbsent(
                    file.getBlob().getId(),
                    ignored -> new BlobDeletionCandidate(file.getBlob())
            );
            candidate.referencesToDelete += 1;
        }

        List<FileBlob> blobsToDelete = new ArrayList<>();
        for (BlobDeletionCandidate candidate : candidates.values()) {
            long currentReferences = storedFileRepository.countByBlobId(candidate.blob.getId());
            if (currentReferences == candidate.referencesToDelete) {
                blobsToDelete.add(candidate.blob);
            }
        }
        return blobsToDelete;
    }

    private void deleteBlobs(List<FileBlob> blobsToDelete) {
        for (FileBlob blob : blobsToDelete) {
            fileContentStorage.deleteBlob(blob.getObjectKey());
            fileBlobRepository.delete(blob);
        }
    }

    private static final class BlobDeletionCandidate {
        private final FileBlob blob;
        private long referencesToDelete;

        private BlobDeletionCandidate(FileBlob blob) {
            this.blob = blob;
        }
    }

    @FunctionalInterface
    private interface BlobWriteOperation<T> {
        T run();
    }

    public static record ZipCompatibleArchive(List<ZipCompatibleArchiveEntry> entries, String commonRootDirectoryName) {
    }

    public static record ZipCompatibleArchiveEntry(String relativePath, boolean directory, byte[] content) {
    }

    public static record ExternalFileImport(String path, String filename, String contentType, byte[] content) {
        public long size() {
            return content == null ? 0L : content.length;
        }
    }

    public record ArchiveSourceSummary(int fileCount, int directoryCount) {
    }

    public record ArchiveBuildProgress(int processedFileCount,
                                       int totalFileCount,
                                       int processedDirectoryCount,
                                       int totalDirectoryCount) {
    }

    @FunctionalInterface
    public interface ArchiveBuildProgressListener {
        void onProgress(ArchiveBuildProgress progress);
    }

    public record ExternalImportProgress(int processedFileCount,
                                         int totalFileCount,
                                         int processedDirectoryCount,
                                         int totalDirectoryCount) {
    }

    @FunctionalInterface
    public interface ExternalImportProgressListener {
        void onProgress(ExternalImportProgress progress);
    }

    private static final class ArchiveBuildProgressState {
        private final ArchiveBuildProgressListener listener;
        private final int totalFileCount;
        private final int totalDirectoryCount;
        private int processedFileCount;
        private int processedDirectoryCount;

        private ArchiveBuildProgressState(ArchiveBuildProgressListener listener,
                                          int totalFileCount,
                                          int totalDirectoryCount) {
            this.listener = listener;
            this.totalFileCount = totalFileCount;
            this.totalDirectoryCount = totalDirectoryCount;
        }
    }
}
