package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.search.api.FileEventType;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.upload.CompleteUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import com.yoyuzh.files.workspace.api.DownloadUrlResponse;
import com.yoyuzh.files.workspace.api.FavoriteFileResponse;
import com.yoyuzh.files.workspace.api.FileDetailResponse;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.RecycleBinItemResponse;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveApi;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveBuildProgressListener;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveExtractionResult;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveSummary;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadMetricsPort;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadOptions;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadResult;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.WorkspaceExternalFileImport;
import com.yoyuzh.files.workspace.api.WorkspaceExternalImportProgress;
import com.yoyuzh.files.workspace.api.WorkspaceExternalImportProgressListener;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleApi;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleResult;
import com.yoyuzh.files.workspace.api.WorkspaceMutationApi;
import com.yoyuzh.files.workspace.api.WorkspaceMutationResult;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.api.WorkspaceZipArchive;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.FileListDirectoryCacheService;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.infra.lock.DistributedLockGateway;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class FileService implements WorkspaceBootstrapApi, WorkspaceArchiveApi {
    private static final List<String> DEFAULT_DIRECTORIES = List.of("下载", "文档", "图片");
    private static final long RECYCLE_BIN_RETENTION_DAYS = 10L;
    private static final String SECURE_LINK_SIGNATURE_ALGORITHM = "HmacSHA256";

    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;
    private final long maxFileSize;
    private final String packageDownloadBaseUrl;
    private final String packageDownloadSecret;
    private final long packageDownloadTtlSeconds;
    private final Clock clock;
    private final WorkspaceNodeRulesService workspaceNodeRulesService;
    private final WorkspaceDirectoryApi workspaceDirectoryApi;
    private final WorkspaceMutationApi workspaceMutationApi;
    private final WorkspaceLifecycleApi workspaceLifecycleApi;
    private final FileUploadRulesService fileUploadRulesService;
    private final ExternalImportRulesService externalImportRulesService;
    private final ContentBlobLifecycleApi contentBlobLifecycleApi;
    private final WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort;
    private final FileListDirectoryCacheService fileListDirectoryCacheService;
    private final WorkspaceFileIngressService workspaceFileIngressService;
    private final WorkspaceFileActivityService workspaceFileActivityService;
    private final WorkspaceArchiveService workspaceArchiveService;
    private final DistributedLockGateway distributedLockGateway;

    @Autowired
    public FileService(StoredFileRepository storedFileRepository,
                       FileContentStorage fileContentStorage,
                       WorkspaceDownloadOptions workspaceDownloadOptions,
                       WorkspaceNodeRulesService workspaceNodeRulesService,
                       WorkspaceDirectoryApi workspaceDirectoryApi,
                       WorkspaceMutationApi workspaceMutationApi,
                       WorkspaceLifecycleApi workspaceLifecycleApi,
                       FileUploadRulesService fileUploadRulesService,
                       ExternalImportRulesService externalImportRulesService,
                       ContentBlobLifecycleApi contentBlobLifecycleApi,
                       WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                       WorkspaceFileIngressService workspaceFileIngressService,
                       WorkspaceFileActivityService workspaceFileActivityService,
                       WorkspaceArchiveService workspaceArchiveService,
                       ObjectProvider<FileListDirectoryCacheService> fileListDirectoryCacheService,
                       ObjectProvider<DistributedLockGateway> distributedLockGateway) {
        this(
                storedFileRepository,
                fileContentStorage,
                workspaceDownloadOptions,
                workspaceNodeRulesService,
                workspaceDirectoryApi,
                workspaceMutationApi,
                workspaceLifecycleApi,
                fileUploadRulesService,
                externalImportRulesService,
                contentBlobLifecycleApi,
                workspaceDownloadMetricsPort,
                fileListDirectoryCacheService.getIfAvailable(FileListDirectoryCacheService::noOp),
                workspaceFileIngressService,
                workspaceFileActivityService,
                workspaceArchiveService,
                distributedLockGateway.getIfAvailable(DistributedLockGateway::noOp),
                0L,
                Clock.systemUTC()
        );
    }

    FileService(StoredFileRepository storedFileRepository,
                FileContentStorage fileContentStorage,
                WorkspaceDownloadOptions workspaceDownloadOptions,
                WorkspaceNodeRulesService workspaceNodeRulesService,
                WorkspaceDirectoryApi workspaceDirectoryApi,
                WorkspaceMutationApi workspaceMutationApi,
                WorkspaceLifecycleApi workspaceLifecycleApi,
                FileUploadRulesService fileUploadRulesService,
                ExternalImportRulesService externalImportRulesService,
                ContentBlobLifecycleApi contentBlobLifecycleApi,
                WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                FileListDirectoryCacheService fileListDirectoryCacheService,
                WorkspaceFileIngressService workspaceFileIngressService,
                WorkspaceFileActivityService workspaceFileActivityService,
                WorkspaceArchiveService workspaceArchiveService,
                DistributedLockGateway distributedLockGateway,
                long maxFileSize,
                Clock clock) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
        this.maxFileSize = maxFileSize;
        this.packageDownloadBaseUrl = workspaceDownloadOptions != null && StringUtils.hasText(workspaceDownloadOptions.packageDownloadBaseUrl())
                ? workspaceDownloadOptions.packageDownloadBaseUrl().trim()
                : null;
        this.packageDownloadSecret = workspaceDownloadOptions != null && StringUtils.hasText(workspaceDownloadOptions.packageDownloadSecret())
                ? workspaceDownloadOptions.packageDownloadSecret().trim()
                : null;
        this.packageDownloadTtlSeconds = workspaceDownloadOptions == null ? 300L : Math.max(1, workspaceDownloadOptions.packageDownloadTtlSeconds());
        this.clock = clock;
        this.workspaceNodeRulesService = workspaceNodeRulesService;
        this.workspaceDirectoryApi = workspaceDirectoryApi;
        this.workspaceMutationApi = workspaceMutationApi;
        this.workspaceLifecycleApi = workspaceLifecycleApi;
        this.fileUploadRulesService = fileUploadRulesService;
        this.externalImportRulesService = externalImportRulesService;
        this.contentBlobLifecycleApi = contentBlobLifecycleApi;
        this.workspaceDownloadMetricsPort = workspaceDownloadMetricsPort == null
                ? WorkspaceDownloadMetricsPort.noOp()
                : workspaceDownloadMetricsPort;
        this.fileListDirectoryCacheService = fileListDirectoryCacheService == null
                ? FileListDirectoryCacheService.noOp()
                : fileListDirectoryCacheService;
        this.workspaceFileIngressService = workspaceFileIngressService;
        this.workspaceFileActivityService = workspaceFileActivityService;
        this.workspaceArchiveService = workspaceArchiveService;
        this.distributedLockGateway = distributedLockGateway == null
                ? DistributedLockGateway.noOp()
                : distributedLockGateway;
    }

    private static RuntimeWorkspacePathPolicy createWorkspacePathPolicy(StoredFileRepository storedFileRepository,
                                                                        FileContentStorage fileContentStorage) {
        return new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
    }

    private static WorkspaceNodeRulesService createWorkspaceNodeRulesService(StoredFileRepository storedFileRepository,
                                                                            FileContentStorage fileContentStorage) {
        RuntimeWorkspacePathPolicy workspacePathPolicy = createWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        return new WorkspaceNodeRulesService(workspacePathPolicy, workspacePathPolicy);
    }

    private static WorkspaceDirectoryApi createWorkspaceDirectoryApi(StoredFileRepository storedFileRepository,
                                                                     FileContentStorage fileContentStorage) {
        RuntimeWorkspacePathPolicy workspacePathPolicy = createWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        return new RuntimeWorkspaceDirectoryApi(storedFileRepository, fileContentStorage, workspacePathPolicy);
    }

    private static WorkspaceMutationApi createWorkspaceMutationApi(StoredFileRepository storedFileRepository,
                                                                   FileContentStorage fileContentStorage) {
        return new RuntimeWorkspaceMutationApi(storedFileRepository, createWorkspacePathPolicy(storedFileRepository, fileContentStorage));
    }

    private static FileUploadRulesService createFileUploadRulesService(StoredFileRepository storedFileRepository,
                                                                       com.yoyuzh.platform.storage.api.StoragePolicyQuery storagePolicyQuery,
                                                                       com.yoyuzh.platform.storage.api.UploadConstraintPolicy uploadConstraintPolicy,
                                                                       FileContentStorage fileContentStorage,
                                                                       long maxFileSize) {
        return new FileUploadRulesService(
                storedFileRepository,
                storagePolicyQuery,
                uploadConstraintPolicy,
                createWorkspaceNodeRulesService(storedFileRepository, fileContentStorage),
                maxFileSize
        );
    }

    private static ExternalImportRulesService createExternalImportRulesService(StoredFileRepository storedFileRepository,
                                                                               com.yoyuzh.platform.storage.api.StoragePolicyQuery storagePolicyQuery,
                                                                               com.yoyuzh.platform.storage.api.UploadConstraintPolicy uploadConstraintPolicy,
                                                                               FileContentStorage fileContentStorage,
                                                                               long maxFileSize) {
        WorkspaceNodeRulesService workspaceNodeRulesService = createWorkspaceNodeRulesService(storedFileRepository, fileContentStorage);
        return new ExternalImportRulesService(
                workspaceNodeRulesService,
                new FileUploadRulesService(
                        storedFileRepository,
                        storagePolicyQuery,
                        uploadConstraintPolicy,
                        workspaceNodeRulesService,
                        maxFileSize
                )
        );
    }

    @Transactional
    public FileMetadataResponse upload(IdentityAuthenticatedUser user, String path, MultipartFile multipartFile) {
        return upload(toWorkspaceUser(user), path, multipartFile);
    }

    @Transactional
    public FileMetadataResponse upload(WorkspaceUserContext user, String path, MultipartFile multipartFile) {
        WorkspaceFileIngressService.CreatedFile createdFile = workspaceFileIngressService.upload(
                user,
                path,
                multipartFile,
                this::resolveUploadedContentType
        );
        return finalizeUploadedFile(user, createdFile.normalizedPath(), createdFile.file());
    }

    public InitiateUploadResponse initiateUpload(IdentityAuthenticatedUser user, InitiateUploadRequest request) {
        return initiateUpload(toWorkspaceUser(user), request);
    }

    public InitiateUploadResponse initiateUpload(WorkspaceUserContext user, InitiateUploadRequest request) {
        return workspaceFileIngressService.initiateUpload(
                user,
                request,
                this::resolveUploadedContentType
        );
    }

    @Transactional
    public FileMetadataResponse completeUpload(IdentityAuthenticatedUser user, CompleteUploadRequest request) {
        return completeUpload(toWorkspaceUser(user), request);
    }

    @Transactional
    public FileMetadataResponse completeUpload(WorkspaceUserContext user, CompleteUploadRequest request) {
        WorkspaceFileIngressService.CreatedFile createdFile = workspaceFileIngressService.completeUpload(
                user,
                request,
                this::resolveUploadedContentType
        );
        return finalizeUploadedFile(user, createdFile.normalizedPath(), createdFile.file());
    }

    @Transactional
    public FileMetadataResponse mkdir(Long userId, String path) {
        return mkdir(toWorkspaceUser(userId), path);
    }

    @Transactional
    public FileMetadataResponse mkdir(WorkspaceUserContext user, String path) {
        String normalizedPath = normalizeDirectoryPath(path);
        FileMetadataResponse response = workspaceDirectoryApi.createDirectory(user.userId(), normalizedPath);
        String parentPath = extractParentPath(normalizedPath);
        workspaceFileActivityService.touchDirectories(user, parentPath);
        return response;
    }

    public PageResponse<FileMetadataResponse> list(Long userId, String path, int page, int size) {
        return list(toWorkspaceUser(userId), path, page, size);
    }

    public PageResponse<FileMetadataResponse> list(WorkspaceUserContext user, String path, int page, int size) {
        String normalizedPath = normalizeDirectoryPath(path);
        PageResponse<FileMetadataResponse> response = fileListDirectoryCacheService.getOrLoad(
                user.userId(),
                normalizedPath,
                page,
                size,
                () -> workspaceDirectoryApi.loadDirectoryPage(user.userId(), normalizedPath, page, size)
        );
        return populateDirectoryChildFlags(user.userId(), response);
    }

    public List<FileMetadataResponse> recent(Long userId) {
        return recent(toWorkspaceUser(userId));
    }

    public List<FileMetadataResponse> recent(WorkspaceUserContext user) {
        return storedFileRepository.findTop12ByUserIdAndDirectoryFalseAndDeletedAtIsNullOrderByCreatedAtDesc(user.userId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FileDetailResponse detail(Long userId, Long fileId) {
        return detail(toWorkspaceUser(userId), fileId);
    }

    public FileDetailResponse detail(WorkspaceUserContext user, Long fileId) {
        StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, user.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        return toDetailResponse(file, false);
    }

    @Transactional
    public void batchDelete(Long userId, List<Long> fileIds) {
        batchDelete(toWorkspaceUser(userId), fileIds);
    }

    @Transactional
    public void batchDelete(WorkspaceUserContext user, List<Long> fileIds) {
        for (Long fileId : fileIds) {
            delete(user, fileId);
        }
    }

    @Transactional
    public FavoriteFileResponse setFavorite(Long userId, Long fileId, boolean favorite) {
        return setFavorite(toWorkspaceUser(userId), fileId, favorite);
    }

    @Transactional
    public FavoriteFileResponse setFavorite(WorkspaceUserContext user, Long fileId, boolean favorite) {
        StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, user.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        file.markFavorite(favorite);
        storedFileRepository.save(file);
        return new FavoriteFileResponse(file.getId(), file.isFavorite());
    }

    public List<FavoriteFileResponse> listFavorites(Long userId) {
        return listFavorites(toWorkspaceUser(userId));
    }

    public List<FavoriteFileResponse> listFavorites(WorkspaceUserContext user) {
        return storedFileRepository.findTop20ByUserIdAndFavoriteTrueAndDeletedAtIsNullOrderByUpdatedAtDesc(user.userId())
                .stream()
                .map(file -> new FavoriteFileResponse(file.getId(), true))
                .toList();
    }

    public PageResponse<RecycleBinItemResponse> listRecycleBin(Long userId, int page, int size) {
        return listRecycleBin(toWorkspaceUser(userId), page, size);
    }

    public PageResponse<RecycleBinItemResponse> listRecycleBin(WorkspaceUserContext user, int page, int size) {
        Page<StoredFile> result = storedFileRepository.findRecycleBinRootsByUserId(user.userId(), PageRequest.of(page, size));
        List<RecycleBinItemResponse> items = result.getContent().stream().map(this::toRecycleBinResponse).toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    @Override
    @Transactional
    public void ensureDefaultDirectories(WorkspaceUserContext user) {
        ensureDefaultDirectoriesInternal(normalizeWorkspaceUser(user));
    }

    @Override
    public boolean existsNode(WorkspaceUserContext user, String path, String filename) {
        WorkspaceUserContext workspaceUser = normalizeWorkspaceUser(user);
        return storedFileRepository.existsByUserIdAndPathAndFilename(
                workspaceUser.userId(),
                normalizeDirectoryPath(path),
                normalizeLeafName(filename)
        );
    }

    @Override
    @Transactional
    public FileMetadataResponse importExternalFile(WorkspaceUserContext user,
                                                   String path,
                                                   String filename,
                                                   String contentType,
                                                   long size,
                                                   byte[] content) {
        return importExternalFileInternal(normalizeWorkspaceUser(user), path, filename, contentType, size, content);
    }

    @Override
    @Transactional
    public void importExternalFilesAtomically(WorkspaceUserContext user,
                                              List<String> directories,
                                              List<WorkspaceExternalFileImport> files,
                                              WorkspaceExternalImportProgressListener progressListener) {
        importExternalFilesAtomically(
                normalizeWorkspaceUser(user),
                directories,
                files.stream()
                        .map(file -> new ExternalFileImport(file.path(), file.filename(), file.contentType(), file.size(), file::openStream))
                        .toList(),
                progressListener == null
                        ? null
                        : progress -> progressListener.onProgress(new WorkspaceExternalImportProgress(
                        progress.processedFileCount(),
                        progress.totalFileCount(),
                        progress.processedDirectoryCount(),
                        progress.totalDirectoryCount()
                ))
        );
    }

    @Override
    public WorkspaceArchiveSummary summarizeArchiveSource(Long userId, Long fileId) {
        StoredFile source = getOwnedActiveFile(toWorkspaceUser(userId), fileId, "归档");
        return workspaceArchiveService.summarizeArchiveSource(source);
    }

    @Override
    public byte[] buildArchiveBytes(Long userId, Long fileId, WorkspaceArchiveBuildProgressListener progressListener) {
        StoredFile source = getOwnedActiveFile(toWorkspaceUser(userId), fileId, "归档");
        return workspaceArchiveService.buildArchiveBytes(source, progressListener);
    }

    @Override
    public WorkspaceZipArchive readZipCompatibleArchive(Long userId, Long fileId) {
        StoredFile source = getOwnedActiveFile(toWorkspaceUser(userId), fileId, "解压");
        return workspaceArchiveService.readZipCompatibleArchive(source, maxFileSize);
    }

    @Override
    @Transactional
    public WorkspaceArchiveExtractionResult extractZipCompatibleArchive(WorkspaceUserContext user,
                                                                       Long fileId,
                                                                       String outputPath,
                                                                       String outputDirectoryName,
                                                                       WorkspaceExternalImportProgressListener progressListener) {
        WorkspaceUserContext workspaceUser = normalizeWorkspaceUser(user);
        StoredFile source = getOwnedActiveFile(workspaceUser, fileId, "解压");
        return workspaceArchiveService.extractZipCompatibleArchive(
                workspaceUser,
                source,
                outputPath,
                outputDirectoryName,
                progressListener,
                maxFileSize
        );
    }

    @Transactional
    private void ensureDefaultDirectoriesInternal(WorkspaceUserContext user) {
        boolean createdAny = false;
        for (String directoryName : DEFAULT_DIRECTORIES) {
            if (workspaceNodeRulesService.existsNodeName(user.userId(), "/", directoryName)) {
                continue;
            }

            String logicalPath = "/" + directoryName;
            fileContentStorage.ensureDirectory(user.userId(), logicalPath);

            storedFileRepository.save(StoredFile.directory(user.userId(), "/", directoryName));
            createdAny = true;
        }
        if (createdAny) {
            workspaceFileActivityService.touchDirectories(user, "/");
        }
    }

    @Transactional
    public void delete(Long userId, Long fileId) {
        delete(toWorkspaceUser(userId), fileId);
    }

    @Transactional
    public void delete(WorkspaceUserContext user, Long fileId) {
        WorkspaceLifecycleResult result = workspaceLifecycleApi.recycle(user.userId(), fileId);
        if (!result.affectedPaths().isEmpty()) {
            workspaceFileActivityService.touchDirectories(user, result.affectedPaths().toArray(String[]::new));
        }
        workspaceFileActivityService.recordMutation(user, FileEventType.DELETED, result.file(), result.fromPath(), result.toPath());
    }

    @Transactional
    public FileMetadataResponse restoreFromRecycleBin(Long userId, Long fileId) {
        return restoreFromRecycleBin(toWorkspaceUser(userId), fileId);
    }

    @Transactional
    public FileMetadataResponse restoreFromRecycleBin(WorkspaceUserContext user, Long fileId) {
        return distributedLockGateway.executeWithLock(
                "files:recycle-restore:" + fileId,
                Duration.ofSeconds(120),
                () -> {
                    WorkspaceLifecycleResult result = workspaceLifecycleApi.restore(
                            user.userId(),
                            fileId,
                            additionalBytes -> fileUploadRulesService.ensureWithinStorageQuota(user, additionalBytes)
                    );
                    if (!result.affectedPaths().isEmpty()) {
                        workspaceFileActivityService.touchDirectories(user, result.affectedPaths().toArray(String[]::new));
                    }
                    workspaceFileActivityService.recordMutation(user, FileEventType.RESTORED, result.file(), result.fromPath(), result.toPath());
                    return result.file();
                }
        );
    }

    @Transactional
    public void pruneExpiredRecycleBinItems() {
        List<StoredFile> expiredItems = storedFileRepository.findByDeletedAtBefore(LocalDateTime.now().minusDays(RECYCLE_BIN_RETENTION_DAYS));
        if (expiredItems.isEmpty()) {
            return;
        }

        List<ContentBlobReference> blobsToDelete = contentBlobLifecycleApi.collectBlobReferencesToDelete(
                expiredItems.stream()
                        .map(StoredFile::getBlobId)
                        .filter(Objects::nonNull)
                        .toList()
        );
        storedFileRepository.deleteAll(expiredItems);
        contentBlobLifecycleApi.deleteBlobReferences(blobsToDelete);
    }

    @Transactional
    public FileMetadataResponse rename(Long userId, Long fileId, String nextFilename) {
        return rename(toWorkspaceUser(userId), fileId, nextFilename);
    }

    @Transactional
    public FileMetadataResponse rename(WorkspaceUserContext user, Long fileId, String nextFilename) {
        String sanitizedFilename = normalizeLeafName(nextFilename);
        WorkspaceMutationResult result = workspaceMutationApi.rename(user.userId(), fileId, sanitizedFilename);
        if (!result.affectedPaths().isEmpty()) {
            workspaceFileActivityService.touchDirectories(user, result.affectedPaths().toArray(String[]::new));
        }
        if (!result.fromPath().equals(result.toPath())) {
            workspaceFileActivityService.recordMutation(user, FileEventType.RENAMED, result.file(), result.fromPath(), result.toPath());
        }
        return result.file();
    }

    @Transactional
    public FileMetadataResponse move(Long userId, Long fileId, String nextPath) {
        return move(toWorkspaceUser(userId), fileId, nextPath);
    }

    @Transactional
    public FileMetadataResponse move(WorkspaceUserContext user, Long fileId, String nextPath) {
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        WorkspaceMutationResult result = workspaceMutationApi.move(user.userId(), fileId, normalizedTargetPath);
        if (!result.affectedPaths().isEmpty()) {
            workspaceFileActivityService.touchDirectories(user, result.affectedPaths().toArray(String[]::new));
        }
        if (!result.fromPath().equals(result.toPath())) {
            workspaceFileActivityService.recordMutation(user, FileEventType.MOVED, result.file(), result.fromPath(), result.toPath());
        }
        return result.file();
    }

    @Transactional
    public FileMetadataResponse copy(Long userId, Long fileId, String nextPath) {
        return copy(toWorkspaceUser(userId), fileId, nextPath);
    }

    @Transactional
    public FileMetadataResponse copy(WorkspaceUserContext user, Long fileId, String nextPath) {
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        WorkspaceLifecycleResult result = workspaceLifecycleApi.copy(
                user.userId(),
                fileId,
                normalizedTargetPath,
                additionalBytes -> fileUploadRulesService.ensureWithinStorageQuota(user, additionalBytes)
        );
        if (!result.affectedPaths().isEmpty()) {
            workspaceFileActivityService.touchDirectories(user, result.affectedPaths().toArray(String[]::new));
        }
        return result.file();
    }

    public WorkspaceDownloadResult download(Long userId, Long fileId) {
        return download(toWorkspaceUser(userId), fileId);
    }

    public WorkspaceDownloadResult download(WorkspaceUserContext user, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "下载");
        if (storedFile.isDirectory()) {
            return downloadDirectory(user, storedFile);
        }

        if (shouldUsePublicPackageDownload(storedFile)) {
            recordWorkspaceDownloadTraffic(storedFile.getSize());
            return WorkspaceDownloadResult.redirect(buildPublicPackageDownloadUrl(storedFile));
        }

        if (fileContentStorage.supportsDirectDownload()) {
            recordWorkspaceDownloadTraffic(storedFile.getSize());
            return WorkspaceDownloadResult.redirect(fileContentStorage.createBlobDownloadUrl(
                    getRequiredBlob(storedFile).objectKey(),
                    storedFile.getFilename()
            ));
        }

        recordWorkspaceDownloadTraffic(storedFile.getSize());
        return WorkspaceDownloadResult.inline(
                storedFile.getFilename(),
                storedFile.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : storedFile.getContentType(),
                fileContentStorage.readBlob(getRequiredBlob(storedFile).objectKey())
        );
    }

    public DownloadUrlResponse getDownloadUrl(Long userId, Long fileId) {
        return getDownloadUrl(toWorkspaceUser(userId), fileId);
    }

    public DownloadUrlResponse getDownloadUrl(WorkspaceUserContext user, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "下载");
        if (storedFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录不支持下载");
        }

        if (shouldUsePublicPackageDownload(storedFile)) {
            return new DownloadUrlResponse(buildPublicPackageDownloadUrl(storedFile));
        }

        if (fileContentStorage.supportsDirectDownload()) {
            return new DownloadUrlResponse(fileContentStorage.createBlobDownloadUrl(
                    getRequiredBlob(storedFile).objectKey(),
                    storedFile.getFilename()
            ));
        }

        return new DownloadUrlResponse("/api/files/download/" + storedFile.getId());
    }

    @Transactional
    private FileMetadataResponse importExternalFileInternal(WorkspaceUserContext recipient,
                                                            String path,
                                                            String filename,
                                                            String contentType,
                                                            long size,
                                                            byte[] content) {
        WorkspaceFileIngressService.CreatedFile createdFile = workspaceFileIngressService.importExternalFile(
                recipient,
                path,
                filename,
                contentType,
                size,
                content
        );
        return finalizeUploadedFile(recipient, createdFile.normalizedPath(), createdFile.file());
    }

    @Transactional
    public void importExternalFilesAtomically(WorkspaceUserContext recipient,
                                              List<String> directories,
                                              List<ExternalFileImport> files) {
        importExternalFilesAtomically(recipient, directories, files, null);
    }

    @Transactional
    public void importExternalFilesAtomically(WorkspaceUserContext recipient,
                                              List<String> directories,
                                              List<ExternalFileImport> files,
                                              ExternalImportProgressListener progressListener) {
        List<String> normalizedDirectories = externalImportRulesService.normalizeDirectories(directories);
        List<ExternalFileImport> normalizedFiles = externalImportRulesService.normalizeFiles(files);
        externalImportRulesService.validateBatch(recipient, normalizedDirectories, normalizedFiles);

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
            List<WorkspaceFileIngressService.CreatedFile> createdFiles = workspaceFileIngressService.storeExternalFiles(
                    recipient,
                    normalizedFiles,
                    writtenBlobObjectKeys
            );
            for (WorkspaceFileIngressService.CreatedFile createdFile : createdFiles) {
                finalizeUploadedFile(recipient, createdFile.normalizedPath(), createdFile.file());
                processedFileCount += 1;
                reportExternalImportProgress(progressListener, processedFileCount, totalFileCount,
                        processedDirectoryCount, totalDirectoryCount);
            }
        } catch (RuntimeException ex) {
            workspaceFileIngressService.cleanupWrittenBlobs(writtenBlobObjectKeys, ex);
            throw ex;
        }
    }

    private WorkspaceDownloadResult downloadDirectory(WorkspaceUserContext user, StoredFile directory) {
        String archiveName = directory.getFilename() + ".zip";
        byte[] archiveBytes = buildArchiveBytes(directory);
        recordWorkspaceDownloadTraffic((long) archiveBytes.length);
        return WorkspaceDownloadResult.inline(archiveName, "application/zip", archiveBytes);
    }

    private void recordWorkspaceDownloadTraffic(Long bytes) {
        if (bytes == null || bytes <= 0L) {
            return;
        }
        workspaceDownloadMetricsPort.recordDownloadTraffic(bytes);
    }

    public byte[] buildArchiveBytes(StoredFile source) {
        return workspaceArchiveService.buildArchiveBytes(source, null);
    }

    public WorkspaceZipArchive readZipCompatibleArchive(StoredFile source) {
        return workspaceArchiveService.readZipCompatibleArchive(source, maxFileSize);
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
        ContentBlobReference blob = getRequiredBlob(storedFile);
        String base = packageDownloadBaseUrl.endsWith("/")
                ? packageDownloadBaseUrl.substring(0, packageDownloadBaseUrl.length() - 1)
                : packageDownloadBaseUrl;
        String path = "/" + trimLeadingSlash(blob.objectKey());
        if (base.endsWith("/_dl")) {
            path = "/_dl" + path;
        }
        long expires = clock.instant().getEpochSecond() + packageDownloadTtlSeconds;
        String signature = buildSecureLinkSignature(path, expires);
        return base
                + "/"
                + trimLeadingSlash(blob.objectKey())
                + "?signature="
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
            Mac mac = Mac.getInstance(SECURE_LINK_SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(packageDownloadSecret.getBytes(StandardCharsets.UTF_8), SECURE_LINK_SIGNATURE_ALGORITHM));
            byte[] hash = mac.doFinal((expires + path).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("生成下载签名失败", ex);
        }
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private FileMetadataResponse finalizeUploadedFile(WorkspaceUserContext user,
                                                      String normalizedPath,
                                                      RegisteredContentFile savedFile) {
        workspaceFileActivityService.afterFileCreated(user, normalizedPath, savedFile);
        return toResponse(savedFile);
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

    private FileDetailResponse toDetailResponse(StoredFile file, boolean shared) {
        return new FileDetailResponse(
                file.getId(),
                file.getFilename(),
                file.getPath(),
                file.getSize(),
                file.getContentType(),
                file.isDirectory(),
                file.isFavorite(),
                shared,
                file.getCreatedAt(),
                file.getUpdatedAt(),
                List.of()
        );
    }

    private StoredFile getOwnedFile(WorkspaceUserContext user, Long fileId, String action) {
        StoredFile storedFile = storedFileRepository.findDetailedById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!user.userId().equals(storedFile.getUserId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限" + action + "该文件");
        }
        return storedFile;
    }

    private StoredFile getOwnedActiveFile(WorkspaceUserContext user, Long fileId, String action) {
        StoredFile storedFile = getOwnedFile(user, fileId, action);
        if (storedFile.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }
        return storedFile;
    }

    private String requireRecycleOriginalPath(StoredFile storedFile) {
        if (!StringUtils.hasText(storedFile.getRecycleOriginalPath())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return storedFile.getRecycleOriginalPath();
    }

    private String resolveUploadedContentType(String filename, String reportedContentType) {
        String normalizedReportedType = StringUtils.hasText(reportedContentType)
                ? reportedContentType.trim().toLowerCase(Locale.ROOT)
                : "";
        String inferredContentType = inferContentTypeFromFilename(filename);
        if (StringUtils.hasText(inferredContentType) && isWeakReportedContentType(normalizedReportedType)) {
            return inferredContentType;
        }
        return StringUtils.hasText(normalizedReportedType)
                ? normalizedReportedType
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private boolean isWeakReportedContentType(String contentType) {
        return !StringUtils.hasText(contentType)
                || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)
                || MediaType.TEXT_PLAIN_VALUE.equals(contentType);
    }

    private String inferContentTypeFromFilename(String filename) {
        return WorkspaceContentTypeResolver.inferContentTypeFromFilename(filename);
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
                storedFile.getCreatedAt(),
                storedFile.getUpdatedAt() != null ? storedFile.getUpdatedAt() : storedFile.getCreatedAt(),
                false);
    }

    private FileMetadataResponse toResponse(RegisteredContentFile storedFile) {
        return new FileMetadataResponse(
                storedFile.id(),
                storedFile.filename(),
                storedFile.path(),
                storedFile.size(),
                storedFile.contentType(),
                storedFile.directory(),
                storedFile.createdAt(),
                storedFile.createdAt(),
                false
        );
    }

    private PageResponse<FileMetadataResponse> populateDirectoryChildFlags(Long userId,
                                                                           PageResponse<FileMetadataResponse> response) {
        List<String> directoryPaths = response.items().stream()
                .filter(FileMetadataResponse::directory)
                .map(this::buildLogicalPath)
                .distinct()
                .toList();
        if (directoryPaths.isEmpty()) {
            return response;
        }
        Set<String> directoryPathsWithChildren = Set.copyOf(
                storedFileRepository.findDirectoryPathsWithChildDirectories(userId, directoryPaths)
        );
        List<FileMetadataResponse> items = response.items().stream()
                .map(item -> applyDirectoryChildFlag(item, directoryPathsWithChildren))
                .toList();
        return new PageResponse<>(items, response.total(), response.page(), response.size());
    }

    private FileMetadataResponse applyDirectoryChildFlag(FileMetadataResponse item, Set<String> directoryPathsWithChildren) {
        if (!item.directory()) {
            return item;
        }
        boolean hasChildDirectory = directoryPathsWithChildren.contains(buildLogicalPath(item));
        return new FileMetadataResponse(
                item.id(),
                item.filename(),
                item.path(),
                item.size(),
                item.contentType(),
                true,
                item.createdAt(),
                item.updatedAt(),
                hasChildDirectory
        );
    }

    private String normalizeDirectoryPath(String path) {
        return workspaceNodeRulesService.normalizeDirectoryPath(path);
    }

    private String extractParentPath(String normalizedPath) {
        return workspaceNodeRulesService.extractParentPath(normalizedPath);
    }

    private String extractLeafName(String normalizedPath) {
        return workspaceNodeRulesService.extractLeafName(normalizedPath);
    }

    private String buildLogicalPath(StoredFile storedFile) {
        return buildLogicalPath(storedFile.getPath(), storedFile.getFilename());
    }

    private String buildLogicalPath(FileMetadataResponse item) {
        if (!item.directory()) {
            return item.path();
        }
        if ("/".equals(item.path())) {
            return buildLogicalPath(item.path(), item.filename());
        }
        String suffix = "/" + item.filename();
        return item.path().endsWith(suffix)
                ? item.path()
                : buildLogicalPath(item.path(), item.filename());
    }

    private String buildLogicalPath(String path, String filename) {
        return "/".equals(path)
                ? "/" + filename
                : path + "/" + filename;
    }

    private WorkspaceUserContext normalizeWorkspaceUser(WorkspaceUserContext user) {
        return new WorkspaceUserContext(
                user.userId(),
                user.storageQuotaBytes() == null ? 0L : user.storageQuotaBytes(),
                user.maxUploadSizeBytes() == null ? maxFileSize : user.maxUploadSizeBytes()
        );
    }

    private WorkspaceUserContext toWorkspaceUser(IdentityAuthenticatedUser user) {
        return new WorkspaceUserContext(
                user.id(),
                user.storageQuotaBytes(),
                user.maxUploadSizeBytes()
        );
    }

    private WorkspaceUserContext toWorkspaceUser(Long userId) {
        return new WorkspaceUserContext(userId, Long.MAX_VALUE, maxFileSize);
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

    private String normalizeLeafName(String filename) {
        return workspaceNodeRulesService.normalizeLeafName(filename);
    }

    private ContentBlobReference getRequiredBlob(StoredFile storedFile) {
        return contentBlobLifecycleApi.requireBlobReference(storedFile.getBlobId(), storedFile.isDirectory());
    }

    public static record ExternalFileImport(String path,
                                            String filename,
                                            String contentType,
                                            long size,
                                            WorkspaceExternalFileImport.ContentStreamOpener contentStreamOpener) {
        public ExternalFileImport(String path, String filename, String contentType, byte[] content) {
            this(path, filename, contentType, content == null ? 0L : content.length, new WorkspaceExternalFileImport(path, filename, contentType, content)::openStream);
        }

        public InputStream openStream() throws IOException {
            return contentStreamOpener == null ? InputStream.nullInputStream() : contentStreamOpener.open();
        }

        public byte[] content() {
            try (InputStream inputStream = openStream()) {
                return inputStream.readAllBytes();
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException("failed to read external import content", ex);
            }
        }
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

}
