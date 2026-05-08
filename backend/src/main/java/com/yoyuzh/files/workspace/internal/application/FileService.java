package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.search.api.FileEventType;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.upload.CompleteUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import com.yoyuzh.files.workspace.api.DownloadUrlResponse;
import com.yoyuzh.files.workspace.api.FavoriteFileResponse;
import com.yoyuzh.files.workspace.api.FileDeleteMode;
import com.yoyuzh.files.workspace.api.FileDetailResponse;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.RecycleBinItemResponse;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveApi;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveBuildProgressListener;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveExtractionResult;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveListing;
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
import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import com.yoyuzh.files.workspace.api.WorkspaceMoveItemResult;
import com.yoyuzh.files.workspace.api.WorkspaceMoveOutcomeStatus;
import com.yoyuzh.files.workspace.api.WorkspaceMoveResult;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class FileService implements WorkspaceBootstrapApi, WorkspaceArchiveApi {
    private static final List<String> DEFAULT_DIRECTORIES = List.of("下载", "文档", "图片");
    private static final long RECYCLE_BIN_RETENTION_DAYS = 10L;
    private static final String SECURE_LINK_SIGNATURE_ALGORITHM = "HmacSHA256";
    private static final String FOLDER_COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";
    private static final Set<String> INLINE_EDITABLE_EXTENSIONS = Set.of("txt", "md", "drawio", "excalidraw");

    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;
    private final long maxFileSize;
    private final String publicDownloadBaseUrl;
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
    private final WorkspaceRequestProbe workspaceRequestProbe;

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
                       ObjectProvider<WorkspaceRequestProbe> workspaceRequestProbe,
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
                workspaceRequestProbe.getIfAvailable(WorkspaceRequestProbe::disabled),
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
                WorkspaceRequestProbe workspaceRequestProbe,
                long maxFileSize,
                Clock clock) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
        this.maxFileSize = maxFileSize;
        this.publicDownloadBaseUrl = workspaceDownloadOptions != null && StringUtils.hasText(workspaceDownloadOptions.publicDownloadBaseUrl())
                ? workspaceDownloadOptions.publicDownloadBaseUrl().trim()
                : null;
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
        this.workspaceRequestProbe = workspaceRequestProbe == null
                ? WorkspaceRequestProbe.disabled()
                : workspaceRequestProbe;
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
        WorkspaceFileIngressService.CreatedFile createdFile = workspaceRequestProbe.measure(
                "service.upload.ingress",
                () -> workspaceFileIngressService.upload(
                        user,
                        path,
                        multipartFile,
                        this::resolveUploadedContentType
                )
        );
        return workspaceRequestProbe.measure(
                "service.upload.finalize",
                () -> finalizeUploadedFile(user, createdFile.normalizedPath(), createdFile.file())
        );
    }

    public InitiateUploadResponse initiateUpload(IdentityAuthenticatedUser user, InitiateUploadRequest request) {
        return initiateUpload(toWorkspaceUser(user), request);
    }

    public InitiateUploadResponse initiateUpload(WorkspaceUserContext user, InitiateUploadRequest request) {
        return workspaceRequestProbe.measure(
                "service.upload.initiate",
                () -> workspaceFileIngressService.initiateUpload(
                        user,
                        request,
                        this::resolveUploadedContentType
                )
        );
    }

    @Transactional
    public FileMetadataResponse completeUpload(IdentityAuthenticatedUser user, CompleteUploadRequest request) {
        return completeUpload(toWorkspaceUser(user), request);
    }

    @Transactional
    public FileMetadataResponse completeUpload(WorkspaceUserContext user, CompleteUploadRequest request) {
        WorkspaceFileIngressService.CreatedFile createdFile = workspaceRequestProbe.measure(
                "service.upload.completeIngress",
                () -> workspaceFileIngressService.completeUpload(
                        user,
                        request,
                        this::resolveUploadedContentType
                )
        );
        return workspaceRequestProbe.measure(
                "service.upload.finalize",
                () -> finalizeUploadedFile(user, createdFile.normalizedPath(), createdFile.file())
        );
    }

    @Transactional
    public FileMetadataResponse updateContent(Long userId, Long fileId, MultipartFile multipartFile) {
        return updateContent(toWorkspaceUser(userId), fileId, multipartFile);
    }

    @Transactional
    public FileMetadataResponse updateContent(IdentityAuthenticatedUser user, Long fileId, MultipartFile multipartFile) {
        return updateContent(toWorkspaceUser(user), fileId, multipartFile);
    }

    @Transactional
    public FileMetadataResponse updateContent(WorkspaceUserContext user, Long fileId, MultipartFile multipartFile) {
        WorkspaceUserContext workspaceUser = normalizeWorkspaceUser(user);
        StoredFile storedFile = getOwnedActiveFile(workspaceUser, fileId, "编辑");
        if (storedFile.isDirectory()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "目录不支持在线编辑");
        }
        if (!isInlineEditable(storedFile.getFilename())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "当前文件类型不支持在线编辑");
        }
        if (multipartFile == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "文件内容不能为空");
        }

        String contentType = resolveUploadedContentType(storedFile.getFilename(), multipartFile.getContentType());
        List<ContentBlobReference> oldBlobsToDelete = contentBlobLifecycleApi.collectBlobReferencesToDelete(
                storedFile.getBlobId() == null ? List.of() : List.of(storedFile.getBlobId())
        );
        WorkspaceFileIngressService.ReplacementContent replacement;
        try {
            replacement = workspaceFileIngressService.replaceFileContent(
                    workspaceUser,
                    storedFile.getId(),
                    contentType,
                    multipartFile.getSize(),
                    storedFile.getSize() == null ? 0L : storedFile.getSize(),
                    multipartFile.getInputStream()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read replacement file content", ex);
        }

        storedFile.setBlobId(replacement.blobId());
        storedFile.setPrimaryEntityId(replacement.primaryEntityId());
        storedFile.setLegacyStorageName(replacement.objectKey());
        storedFile.setContentType(contentType);
        storedFile.setSize(multipartFile.getSize());
        StoredFile savedFile = storedFileRepository.save(storedFile);
        workspaceFileActivityService.touchDirectories(workspaceUser, savedFile.getPath());
        workspaceFileActivityService.recordMutation(workspaceUser, FileEventType.UPDATED, savedFile, buildLogicalPath(savedFile), buildLogicalPath(savedFile));
        contentBlobLifecycleApi.deleteBlobReferences(oldBlobsToDelete);
        return toResponse(savedFile);
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
        String normalizedPath = workspaceRequestProbe.measure("service.list.normalizePath", () -> normalizeDirectoryPath(path));
        workspaceRequestProbe.putMetadata("normalizedPath", normalizedPath);
        return workspaceRequestProbe.measure(
                "service.list.cacheLookup",
                () -> fileListDirectoryCacheService.getOrLoad(
                        user.userId(),
                        normalizedPath,
                        page,
                        size,
                        () -> workspaceDirectoryApi.loadDirectoryPage(user.userId(), normalizedPath, page, size)
                )
        );
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
        StoredFile file = workspaceRequestProbe.measure(
                "service.detail.query",
                () -> storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, user.userId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"))
        );
        return workspaceRequestProbe.measure("service.detail.assemble", () -> toDetailResponse(file, false));
    }

    @Transactional
    public void batchDelete(Long userId, List<Long> fileIds) {
        batchDelete(toWorkspaceUser(userId), fileIds, FileDeleteMode.RECYCLE);
    }

    @Transactional
    public void batchDelete(WorkspaceUserContext user, List<Long> fileIds) {
        batchDelete(user, fileIds, FileDeleteMode.RECYCLE);
    }

    @Transactional
    public void batchDelete(Long userId, List<Long> fileIds, FileDeleteMode mode) {
        batchDelete(toWorkspaceUser(userId), fileIds, mode);
    }

    @Transactional
    public void batchDelete(WorkspaceUserContext user, List<Long> fileIds, FileDeleteMode mode) {
        FileDeleteMode deleteMode = normalizeDeleteMode(mode);
        for (Long fileId : fileIds) {
            delete(user, fileId, deleteMode);
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
    public WorkspaceArchiveListing readArchive(Long userId, Long fileId) {
        StoredFile source = getOwnedActiveFile(toWorkspaceUser(userId), fileId, "解压");
        return workspaceArchiveService.readArchive(source, maxFileSize);
    }

    public WorkspaceDownloadResult downloadArchiveEntry(Long userId, Long fileId, String entryPath) {
        StoredFile source = getOwnedActiveFile(toWorkspaceUser(userId), fileId, "解压");
        return workspaceArchiveService.downloadArchiveEntry(source, entryPath, maxFileSize);
    }

    @Override
    @Transactional
    public WorkspaceArchiveExtractionResult extractArchive(WorkspaceUserContext user,
                                                           Long fileId,
                                                           String outputPath,
                                                           String outputDirectoryName,
                                                           WorkspaceExternalImportProgressListener progressListener) {
        WorkspaceUserContext workspaceUser = normalizeWorkspaceUser(user);
        StoredFile source = getOwnedActiveFile(workspaceUser, fileId, "解压");
        return workspaceArchiveService.extractArchive(
                workspaceUser,
                source,
                outputPath,
                outputDirectoryName,
                progressListener,
                maxFileSize
        );
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
        delete(toWorkspaceUser(userId), fileId, FileDeleteMode.RECYCLE);
    }

    @Transactional
    public void delete(WorkspaceUserContext user, Long fileId) {
        delete(user, fileId, FileDeleteMode.RECYCLE);
    }

    @Transactional
    public void delete(Long userId, Long fileId, FileDeleteMode mode) {
        delete(toWorkspaceUser(userId), fileId, mode);
    }

    @Transactional
    public void delete(WorkspaceUserContext user, Long fileId, FileDeleteMode mode) {
        FileDeleteMode deleteMode = normalizeDeleteMode(mode);
        if (deleteMode == FileDeleteMode.PERMANENT) {
            permanentlyDeleteActiveFile(normalizeWorkspaceUser(user), fileId);
            return;
        }
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
        deleteStoredFilesPermanently(expiredItems);
    }

    @Transactional
    public void permanentlyDeleteRecycleBinItem(Long userId, Long fileId) {
        permanentlyDeleteRecycleBinItem(toWorkspaceUser(userId), fileId);
    }

    @Transactional
    public void permanentlyDeleteRecycleBinItem(WorkspaceUserContext user, Long fileId) {
        StoredFile recycleRoot = getOwnedRecycleRootFile(normalizeWorkspaceUser(user), fileId);
        deleteStoredFilesPermanently(loadRecycleGroupItems(recycleRoot));
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
    public WorkspaceMoveResult move(Long userId, Long fileId, String nextPath, WorkspaceMoveConflictStrategy conflictStrategy) {
        return move(toWorkspaceUser(userId), fileId, nextPath, conflictStrategy);
    }

    @Transactional
    public WorkspaceMoveResult move(WorkspaceUserContext user,
                                    Long fileId,
                                    String nextPath,
                                    WorkspaceMoveConflictStrategy conflictStrategy) {
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        StoredFile file = getOwnedActiveFile(user, fileId, "移动");
        WorkspaceMoveResult result = workspaceMutationApi.move(user.userId(), fileId, normalizedTargetPath, conflictStrategy);
        applyMoveSideEffects(user, result, Map.of(file.getId(), file));
        return result;
    }

    @Transactional
    public WorkspaceMoveResult batchMove(Long userId,
                                         List<Long> fileIds,
                                         String nextPath,
                                         WorkspaceMoveConflictStrategy conflictStrategy) {
        return batchMove(toWorkspaceUser(userId), fileIds, nextPath, conflictStrategy);
    }

    @Transactional
    public WorkspaceMoveResult batchMove(WorkspaceUserContext user,
                                         List<Long> fileIds,
                                         String nextPath,
                                         WorkspaceMoveConflictStrategy conflictStrategy) {
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        workspaceNodeRulesService.ensureExistingDirectoryPath(user.userId(), normalizedTargetPath);
        List<StoredFile> files = fileIds.stream()
                .distinct()
                .map(fileId -> getOwnedActiveFile(user, fileId, "移动"))
                .toList();
        WorkspaceMoveResult preflight = inspectBatchMove(user.userId(), files, normalizedTargetPath, conflictStrategy);
        if (preflight != null) {
            return preflight;
        }

        List<WorkspaceMoveItemResult> items = new ArrayList<>();
        for (StoredFile file : files) {
            WorkspaceMoveResult moveResult = workspaceMutationApi.move(
                    user.userId(),
                    file.getId(),
                    normalizedTargetPath,
                    conflictStrategy
            );
            if (moveResult.status() != WorkspaceMoveOutcomeStatus.SUCCESS) {
                return moveResult;
            }
            items.addAll(moveResult.items());
        }

        WorkspaceMoveResult result = WorkspaceMoveResult.success(items);
        applyMoveSideEffects(user, result, indexFilesById(files));
        return result;
    }

    @Transactional
    public FileMetadataResponse updateAppearance(Long userId, Long fileId, String customEmoji, String folderColor) {
        return updateAppearance(toWorkspaceUser(userId), fileId, customEmoji, folderColor);
    }

    @Transactional
    public FileMetadataResponse updateAppearance(WorkspaceUserContext user,
                                                Long fileId,
                                                String customEmoji,
                                                String folderColor) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "更新外观");
        String normalizedEmoji = normalizeCustomEmoji(customEmoji);
        String normalizedFolderColor = normalizeFolderColor(folderColor);
        if (!storedFile.isDirectory() && normalizedFolderColor != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "只有文件夹可以设置颜色");
        }
        storedFile.updateAppearance(normalizedEmoji, normalizedFolderColor);
        StoredFile savedFile = storedFileRepository.save(storedFile);
        return toResponse(savedFile);
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
        StoredFile storedFile = workspaceRequestProbe.measure(
                "service.download.loadFile",
                () -> getOwnedActiveFile(user, fileId, "下载")
        );
        if (storedFile.isDirectory()) {
            return workspaceRequestProbe.measure("service.download.archiveDirectory", () -> downloadDirectory(user, storedFile));
        }

        if (shouldUsePublicPackageDownload(storedFile)) {
            recordWorkspaceDownloadTraffic(storedFile.getSize());
            return workspaceRequestProbe.measure(
                    "service.download.buildPublicUrl",
                    () -> WorkspaceDownloadResult.redirect(buildPublicPackageDownloadUrl(storedFile))
            );
        }

        if (fileContentStorage.supportsDirectDownload()) {
            recordWorkspaceDownloadTraffic(storedFile.getSize());
            ContentBlobReference blob = getRequiredBlob(storedFile);
            return workspaceRequestProbe.measure(
                    "service.download.createDirectUrl",
                    () -> WorkspaceDownloadResult.redirect(fileContentStorage.createBlobDownloadUrl(
                            blob.objectKey(),
                            storedFile.getFilename()
                    ))
            );
        }

        recordWorkspaceDownloadTraffic(storedFile.getSize());
        ContentBlobReference blob = getRequiredBlob(storedFile);
        byte[] body = workspaceRequestProbe.measure(
                "service.download.readBlob",
                () -> fileContentStorage.readBlob(blob.objectKey())
        );
        return WorkspaceDownloadResult.inline(
                storedFile.getFilename(),
                storedFile.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : storedFile.getContentType(),
                body
        );
    }

    public DownloadUrlResponse getDownloadUrl(Long userId, Long fileId) {
        return getDownloadUrl(toWorkspaceUser(userId), fileId);
    }

    public DownloadUrlResponse getDownloadUrl(WorkspaceUserContext user, Long fileId) {
        return new DownloadUrlResponse(resolveDownloadUrl(user, fileId, false));
    }

    public DownloadUrlResponse getViewerSourceUrl(Long userId, Long fileId) {
        return getViewerSourceUrl(toWorkspaceUser(userId), fileId);
    }

    public DownloadUrlResponse getViewerSourceUrl(WorkspaceUserContext user, Long fileId) {
        return new DownloadUrlResponse(resolveDownloadUrl(user, fileId, true));
    }

    private String resolveDownloadUrl(WorkspaceUserContext user, Long fileId, boolean preferPublicViewerUrl) {
        StoredFile storedFile = workspaceRequestProbe.measure(
                "service.downloadUrl.loadFile",
                () -> getOwnedActiveFile(user, fileId, "下载")
        );
        if (storedFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录不支持下载");
        }

        if (shouldUsePublicPackageDownload(storedFile)) {
            return workspaceRequestProbe.measure("service.downloadUrl.buildPublicUrl", () -> buildPublicPackageDownloadUrl(storedFile));
        }

        if (fileContentStorage.supportsDirectDownload()) {
            ContentBlobReference blob = getRequiredBlob(storedFile);
            return workspaceRequestProbe.measure(
                    "service.downloadUrl.createDirectUrl",
                    () -> fileContentStorage.createBlobDownloadUrl(
                            blob.objectKey(),
                            storedFile.getFilename()
                    )
            );
        }

        return "/api/files/download/" + storedFile.getId();
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
        workspaceRequestProbe.measure("service.upload.afterFileCreated", () -> workspaceFileActivityService.afterFileCreated(user, normalizedPath, savedFile));
        return workspaceRequestProbe.measure("service.upload.responseAssemble", () -> toResponse(savedFile));
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
                file.getCustomEmoji(),
                file.getFolderColor(),
                List.of()
        );
    }

    private StoredFile getOwnedFile(WorkspaceUserContext user, Long fileId, String action) {
        Optional<StoredFile> ownedFile = storedFileRepository.findDetailedByIdAndUserId(fileId, user.userId());
        if (ownedFile.isPresent()) {
            return ownedFile.get();
        }
        StoredFile storedFile = storedFileRepository.findDetailedById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!user.userId().equals(storedFile.getUserId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限" + action + "该文件");
        }
        return storedFile;
    }

    private StoredFile getOwnedActiveFile(WorkspaceUserContext user, Long fileId, String action) {
        Optional<StoredFile> activeFile = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, user.userId());
        if (activeFile.isPresent()) {
            return activeFile.get();
        }
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

    private FileDeleteMode normalizeDeleteMode(FileDeleteMode mode) {
        return mode == null ? FileDeleteMode.RECYCLE : mode;
    }

    private void permanentlyDeleteActiveFile(WorkspaceUserContext user, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "直接删除");
        String fromPath = buildLogicalPath(storedFile);
        deleteStoredFilesPermanently(loadActiveDeletionItems(user.userId(), storedFile));
        workspaceFileActivityService.touchDirectories(user, workspaceNodeRulesService.extractParentPath(fromPath));
        workspaceFileActivityService.recordMutation(user, FileEventType.DELETED, storedFile, fromPath, null);
    }

    private List<StoredFile> loadActiveDeletionItems(Long userId, StoredFile storedFile) {
        List<StoredFile> filesToDelete = new ArrayList<>();
        filesToDelete.add(storedFile);
        if (storedFile.isDirectory()) {
            filesToDelete.addAll(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(userId, buildLogicalPath(storedFile)));
        }
        return filesToDelete;
    }

    private List<StoredFile> loadRecycleGroupItems(StoredFile recycleRoot) {
        if (!StringUtils.hasText(recycleRoot.getRecycleGroupId())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        List<StoredFile> items = storedFileRepository.findByRecycleGroupId(recycleRoot.getRecycleGroupId());
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return items;
    }

    private StoredFile getOwnedRecycleRootFile(WorkspaceUserContext user, Long fileId) {
        StoredFile storedFile = getOwnedFile(user, fileId, "永久删除");
        if (storedFile.getDeletedAt() == null || !storedFile.isRecycleRoot()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return storedFile;
    }

    private void deleteStoredFilesPermanently(List<StoredFile> storedFiles) {
        List<ContentBlobReference> blobsToDelete = contentBlobLifecycleApi.collectBlobReferencesToDelete(
                storedFiles.stream()
                        .map(StoredFile::getBlobId)
                        .filter(Objects::nonNull)
                        .toList()
        );
        storedFileRepository.deleteAll(storedFiles);
        contentBlobLifecycleApi.deleteBlobReferences(blobsToDelete);
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

    private boolean isInlineEditable(String filename) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == filename.length() - 1) {
            return false;
        }
        return INLINE_EDITABLE_EXTENSIONS.contains(filename.substring(extensionIndex + 1).toLowerCase(Locale.ROOT));
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
                storedFile.getCustomEmoji(),
                storedFile.getFolderColor(),
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
                null,
                null,
                false
        );
    }

    private void applyMoveSideEffects(WorkspaceUserContext user,
                                      WorkspaceMoveResult result,
                                      Map<Long, StoredFile> filesById) {
        if (result.status() != WorkspaceMoveOutcomeStatus.SUCCESS || result.items().isEmpty()) {
            return;
        }
        Set<String> affectedPaths = new LinkedHashSet<>();
        for (WorkspaceMoveItemResult item : result.items()) {
            if (item.skipped() || item.toPath() == null || item.fromPath() == null || item.fromPath().equals(item.toPath())) {
                continue;
            }
            affectedPaths.add(extractParentPath(item.fromPath()));
            affectedPaths.add(extractParentPath(item.toPath()));
            StoredFile movedFile = filesById.get(item.fileId());
            if (movedFile == null) {
                movedFile = getOwnedActiveFile(user, item.fileId(), "读取移动结果");
            }
            if (movedFile.isDirectory()) {
                affectedPaths.add(item.fromPath());
                affectedPaths.add(item.toPath());
            }
            workspaceFileActivityService.recordMutation(
                    user,
                    FileEventType.MOVED,
                    toResponse(movedFile),
                    item.fromPath(),
                    item.toPath()
            );
        }
        if (!affectedPaths.isEmpty()) {
            workspaceFileActivityService.touchDirectories(user, affectedPaths.toArray(String[]::new));
        }
    }

    private Map<Long, StoredFile> indexFilesById(List<StoredFile> files) {
        return files.stream().collect(java.util.stream.Collectors.toMap(
                StoredFile::getId,
                file -> file,
                (left, right) -> left
        ));
    }

    private WorkspaceMoveResult inspectBatchMove(Long userId,
                                                 List<StoredFile> files,
                                                 String normalizedTargetPath,
                                                 WorkspaceMoveConflictStrategy conflictStrategy) {
        List<WorkspaceMoveItemResult> invalidItems = new ArrayList<>();
        List<WorkspaceMoveItemResult> conflicts = new ArrayList<>();
        Set<String> reservedNames = new LinkedHashSet<>();

        for (StoredFile file : files) {
            String fromPath = buildLogicalPath(file);
            String desiredPath = buildLogicalPath(normalizedTargetPath, file.getFilename());
            if (file.isDirectory() && (desiredPath.equals(fromPath) || desiredPath.startsWith(fromPath + "/"))) {
                invalidItems.add(toMoveItemResult(file, fromPath, null, false, false));
                continue;
            }

            boolean duplicateInTarget = workspaceNodeRulesService.existsNodeName(userId, normalizedTargetPath, file.getFilename());
            boolean duplicateInBatch = !reservedNames.add(file.getFilename());
            if (conflictStrategy == null && (duplicateInTarget || duplicateInBatch)) {
                conflicts.add(toMoveItemResult(file, fromPath, desiredPath, false, false));
            }
        }

        if (!invalidItems.isEmpty()) {
            return WorkspaceMoveResult.invalidTarget("不能移动到当前目录或其子目录", invalidItems);
        }
        if (!conflicts.isEmpty()) {
            return WorkspaceMoveResult.conflict(conflicts);
        }
        return null;
    }

    private WorkspaceMoveItemResult toMoveItemResult(StoredFile file,
                                                     String fromPath,
                                                     String toPath,
                                                     boolean renamed,
                                                     boolean skipped) {
        return new WorkspaceMoveItemResult(
                file.getId(),
                file.getFilename(),
                fromPath,
                toPath,
                renamed,
                skipped,
                file.getCustomEmoji(),
                file.getFolderColor()
        );
    }

    private String normalizeCustomEmoji(String customEmoji) {
        if (!StringUtils.hasText(customEmoji)) {
            return null;
        }
        String normalized = customEmoji.trim();
        if (normalized.codePointCount(0, normalized.length()) > 8) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "自定义图标长度不合法");
        }
        return normalized;
    }

    private String normalizeFolderColor(String folderColor) {
        if (!StringUtils.hasText(folderColor)) {
            return null;
        }
        String normalized = folderColor.trim();
        if (!normalized.matches(FOLDER_COLOR_PATTERN)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "文件夹颜色格式不合法");
        }
        return normalized;
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
        return workspaceRequestProbe.measure(
                "service.blob.requireReference",
                () -> contentBlobLifecycleApi.requireBlobReference(storedFile.getBlobId(), storedFile.isDirectory())
        );
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
