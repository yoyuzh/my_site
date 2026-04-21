package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.internal.application.ContentBlobLifecycleService;
import com.yoyuzh.files.content.internal.application.RuntimeContentAssetApi;
import com.yoyuzh.files.content.internal.application.RuntimeContentRegistrationApi;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.files.search.api.FileEventApi;
import com.yoyuzh.files.search.api.FileEventRecordCommand;
import com.yoyuzh.files.search.api.FileEventType;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.PreparedUpload;
import com.yoyuzh.files.upload.CompleteUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.upload.api.UploadCompletionCommand;
import com.yoyuzh.files.upload.internal.application.RuntimeUploadCompletionApi;
import com.yoyuzh.files.workspace.api.DownloadUrlResponse;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.RecycleBinItemResponse;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveApi;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveBuildProgress;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveBuildProgressListener;
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
import com.yoyuzh.files.workspace.api.WorkspaceZipArchiveEntry;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.FileListDirectoryCacheService;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.infra.lock.DistributedLockGateway;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
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
public class FileService implements WorkspaceBootstrapApi, WorkspaceArchiveApi {
    private static final List<String> DEFAULT_DIRECTORIES = List.of("下载", "文档", "图片");
    private static final long RECYCLE_BIN_RETENTION_DAYS = 10L;

    private final StoredFileRepository storedFileRepository;
    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;
    private final FileContentStorage fileContentStorage;
    private final StoragePolicyQuery storagePolicyQuery;
    private final UploadConstraintPolicy uploadConstraintPolicy;
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
    private final ContentAssetApi contentAssetApi;
    private final ContentRegistrationApi contentRegistrationApi;
    private final UploadCompletionApi uploadCompletionApi;
    private final ContentBlobLifecycleService contentBlobLifecycleService;
    private final WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort;
    private final BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    @Autowired(required = false)
    private FileEventApi fileEventApi;
    @Autowired(required = false)
    private FileListDirectoryCacheService fileListDirectoryCacheService = FileListDirectoryCacheService.noOp();
    @Autowired(required = false)
    private DistributedLockGateway distributedLockGateway = DistributedLockGateway.noOp();

    @Autowired
    public FileService(StoredFileRepository storedFileRepository,
                       FileBlobRepository fileBlobRepository,
                       FileEntityRepository fileEntityRepository,
                       StoredFileEntityRepository storedFileEntityRepository,
                       FileContentStorage fileContentStorage,
                       StoragePolicyQuery storagePolicyQuery,
                       UploadCompletionApi uploadCompletionApi,
                       UploadConstraintPolicy uploadConstraintPolicy,
                       WorkspaceDownloadOptions workspaceDownloadOptions,
                       WorkspaceNodeRulesService workspaceNodeRulesService,
                       WorkspaceDirectoryApi workspaceDirectoryApi,
                       WorkspaceMutationApi workspaceMutationApi,
                       WorkspaceLifecycleApi workspaceLifecycleApi,
                       FileUploadRulesService fileUploadRulesService,
                       ExternalImportRulesService externalImportRulesService,
                       ContentAssetApi contentAssetApi,
                       ContentRegistrationApi contentRegistrationApi,
                       WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                       BackgroundTaskLifecycleApi backgroundTaskLifecycleApi) {
        this(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                fileContentStorage,
                storagePolicyQuery,
                uploadConstraintPolicy,
                workspaceDownloadOptions,
                workspaceNodeRulesService,
                workspaceDirectoryApi,
                workspaceMutationApi,
                workspaceLifecycleApi,
                fileUploadRulesService,
                externalImportRulesService,
                contentAssetApi,
                contentRegistrationApi,
                uploadCompletionApi,
                createContentBlobLifecycleService(storedFileRepository, fileBlobRepository, fileContentStorage),
                workspaceDownloadMetricsPort,
                backgroundTaskLifecycleApi,
                0L,
                Clock.systemUTC()
        );
    }

    public FileService(StoredFileRepository storedFileRepository,
                       FileBlobRepository fileBlobRepository,
                       FileEntityRepository fileEntityRepository,
                       StoredFileEntityRepository storedFileEntityRepository,
                       FileContentStorage fileContentStorage,
                       StoragePolicyQuery storagePolicyQuery,
                       WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                       WorkspaceDownloadOptions workspaceDownloadOptions,
                       long maxFileSize) {
        this(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                fileContentStorage,
                storagePolicyQuery,
                null,
                workspaceDownloadOptions,
                createWorkspaceNodeRulesService(storedFileRepository, fileContentStorage),
                createWorkspaceDirectoryApi(storedFileRepository, fileContentStorage),
                createWorkspaceMutationApi(storedFileRepository, fileContentStorage),
                createWorkspaceLifecycleApi(storedFileRepository, fileContentStorage),
                createFileUploadRulesService(storedFileRepository, storagePolicyQuery, null, fileContentStorage, maxFileSize),
                createExternalImportRulesService(storedFileRepository, storagePolicyQuery, null, fileContentStorage, maxFileSize),
                createContentAssetApi(storedFileRepository, fileEntityRepository, storedFileEntityRepository, storagePolicyQuery),
                createContentRegistrationApi(storedFileRepository, fileEntityRepository, storedFileEntityRepository, storagePolicyQuery),
                createUploadCompletionApi(storedFileRepository, fileEntityRepository, storedFileEntityRepository, fileBlobRepository, fileContentStorage, storagePolicyQuery),
                new ContentBlobLifecycleService(storedFileRepository, fileBlobRepository, fileContentStorage),
                workspaceDownloadMetricsPort,
                null,
                maxFileSize,
                Clock.systemUTC()
        );
    }

    FileService(StoredFileRepository storedFileRepository,
                FileEntityRepository fileEntityRepository,
                StoredFileEntityRepository storedFileEntityRepository,
                FileContentStorage fileContentStorage,
                StoragePolicyQuery storagePolicyQuery,
                UploadConstraintPolicy uploadConstraintPolicy,
                WorkspaceDownloadOptions workspaceDownloadOptions,
                WorkspaceNodeRulesService workspaceNodeRulesService,
                WorkspaceDirectoryApi workspaceDirectoryApi,
                WorkspaceMutationApi workspaceMutationApi,
                WorkspaceLifecycleApi workspaceLifecycleApi,
                FileUploadRulesService fileUploadRulesService,
                ExternalImportRulesService externalImportRulesService,
                ContentAssetApi contentAssetApi,
                ContentRegistrationApi contentRegistrationApi,
                UploadCompletionApi uploadCompletionApi,
                ContentBlobLifecycleService contentBlobLifecycleService,
                WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                BackgroundTaskLifecycleApi backgroundTaskLifecycleApi,
                long maxFileSize,
                Clock clock) {
        this.storedFileRepository = storedFileRepository;
        this.fileEntityRepository = fileEntityRepository;
        this.storedFileEntityRepository = storedFileEntityRepository;
        this.fileContentStorage = fileContentStorage;
        this.storagePolicyQuery = storagePolicyQuery;
        this.uploadConstraintPolicy = uploadConstraintPolicy;
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
        this.contentAssetApi = contentAssetApi;
        this.contentRegistrationApi = contentRegistrationApi;
        this.uploadCompletionApi = uploadCompletionApi;
        this.contentBlobLifecycleService = contentBlobLifecycleService;
        this.workspaceDownloadMetricsPort = workspaceDownloadMetricsPort == null
                ? WorkspaceDownloadMetricsPort.noOp()
                : workspaceDownloadMetricsPort;
        this.backgroundTaskLifecycleApi = backgroundTaskLifecycleApi;
    }

    FileService(StoredFileRepository storedFileRepository,
                FileBlobRepository fileBlobRepository,
                FileContentStorage fileContentStorage,
                WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                WorkspaceDownloadOptions workspaceDownloadOptions,
                long maxFileSize) {
        this(storedFileRepository, fileBlobRepository, null, null, fileContentStorage, null, workspaceDownloadMetricsPort, workspaceDownloadOptions, maxFileSize);
    }

    FileService(StoredFileRepository storedFileRepository,
                FileBlobRepository fileBlobRepository,
                FileContentStorage fileContentStorage,
                WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                WorkspaceDownloadOptions workspaceDownloadOptions,
                long maxFileSize,
                Clock clock) {
        this(
                storedFileRepository,
                null,
                null,
                fileContentStorage,
                null,
                null,
                workspaceDownloadOptions,
                createWorkspaceNodeRulesService(storedFileRepository, fileContentStorage),
                createWorkspaceDirectoryApi(storedFileRepository, fileContentStorage),
                createWorkspaceMutationApi(storedFileRepository, fileContentStorage),
                createWorkspaceLifecycleApi(storedFileRepository, fileContentStorage),
                createFileUploadRulesService(storedFileRepository, null, null, fileContentStorage, maxFileSize),
                createExternalImportRulesService(storedFileRepository, null, null, fileContentStorage, maxFileSize),
                createContentAssetApi(storedFileRepository, null, null, null),
                createContentRegistrationApi(storedFileRepository, null, null, null),
                createUploadCompletionApi(storedFileRepository, null, null, fileBlobRepository, fileContentStorage, null),
                new ContentBlobLifecycleService(storedFileRepository, fileBlobRepository, fileContentStorage),
                workspaceDownloadMetricsPort,
                null,
                maxFileSize,
                clock
        );
    }

    private static RuntimeWorkspacePathPolicy createWorkspacePathPolicy(StoredFileRepository storedFileRepository,
                                                                        FileContentStorage fileContentStorage) {
        return new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
    }

    private static WorkspaceNodeRulesService createWorkspaceNodeRulesService(StoredFileRepository storedFileRepository,
                                                                            FileContentStorage fileContentStorage) {
        return new WorkspaceNodeRulesService(createWorkspacePathPolicy(storedFileRepository, fileContentStorage));
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

    private static WorkspaceLifecycleApi createWorkspaceLifecycleApi(StoredFileRepository storedFileRepository,
                                                                     FileContentStorage fileContentStorage) {
        RuntimeWorkspacePathPolicy workspacePathPolicy = createWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        WorkspaceNodeRulesService workspaceNodeRulesService = new WorkspaceNodeRulesService(workspacePathPolicy);
        RuntimeContentRegistrationApi contentRegistrationApi = new RuntimeContentRegistrationApi(
                storedFileRepository,
                new RuntimeContentAssetApi(storedFileRepository, null, null, null)
        );
        return new RuntimeWorkspaceLifecycleApi(
                storedFileRepository,
                contentRegistrationApi,
                workspacePathPolicy,
                workspaceNodeRulesService
        );
    }

    private static ContentBlobLifecycleService createContentBlobLifecycleService(StoredFileRepository storedFileRepository,
                                                                                 FileBlobRepository fileBlobRepository,
                                                                                 FileContentStorage fileContentStorage) {
        return new ContentBlobLifecycleService(storedFileRepository, fileBlobRepository, fileContentStorage);
    }

    private static FileUploadRulesService createFileUploadRulesService(StoredFileRepository storedFileRepository,
                                                                       StoragePolicyQuery storagePolicyQuery,
                                                                       UploadConstraintPolicy uploadConstraintPolicy,
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
                                                                               StoragePolicyQuery storagePolicyQuery,
                                                                               UploadConstraintPolicy uploadConstraintPolicy,
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

    private static ContentAssetApi createContentAssetApi(StoredFileRepository storedFileRepository,
                                                         FileEntityRepository fileEntityRepository,
                                                         StoredFileEntityRepository storedFileEntityRepository,
                                                         StoragePolicyQuery storagePolicyQuery) {
        return new RuntimeContentAssetApi(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
    }

    private static ContentRegistrationApi createContentRegistrationApi(StoredFileRepository storedFileRepository,
                                                                       FileEntityRepository fileEntityRepository,
                                                                       StoredFileEntityRepository storedFileEntityRepository,
                                                                       StoragePolicyQuery storagePolicyQuery) {
        return new RuntimeContentRegistrationApi(
                storedFileRepository,
                createContentAssetApi(storedFileRepository, fileEntityRepository, storedFileEntityRepository, storagePolicyQuery)
        );
    }

    private static UploadCompletionApi createUploadCompletionApi(StoredFileRepository storedFileRepository,
                                                                 FileEntityRepository fileEntityRepository,
                                                                 StoredFileEntityRepository storedFileEntityRepository,
                                                                 FileBlobRepository fileBlobRepository,
                                                                 FileContentStorage fileContentStorage,
                                                                 StoragePolicyQuery storagePolicyQuery) {
        WorkspacePathPolicy workspacePathPolicy = createWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        ContentRegistrationApi contentRegistrationApi = createContentRegistrationApi(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        return new RuntimeUploadCompletionApi(
                workspacePathPolicy,
                contentRegistrationApi,
                fileBlobRepository,
                fileContentStorage
        );
    }

    @Transactional
    public FileMetadataResponse upload(User user, String path, MultipartFile multipartFile) {
        String normalizedPath = normalizeDirectoryPath(path);
        String filename = normalizeUploadFilename(multipartFile.getOriginalFilename());
        fileUploadRulesService.validateUpload(user, normalizedPath, filename, multipartFile.getSize());
        ensureDirectoryHierarchy(user, normalizedPath);

        String objectKey = createBlobObjectKey();
        return contentBlobLifecycleService.executeAfterBlobStored(objectKey, () -> {
            fileContentStorage.uploadBlob(objectKey, multipartFile);
            FileBlob blob = contentBlobLifecycleService.createAndSaveBlob(objectKey, multipartFile.getContentType(), multipartFile.getSize());
            return saveFileMetadata(user, normalizedPath, filename, multipartFile.getContentType(), multipartFile.getSize(), blob);
        });
    }

    public InitiateUploadResponse initiateUpload(User user, InitiateUploadRequest request) {
        String normalizedPath = normalizeDirectoryPath(request.path());
        String filename = normalizeLeafName(request.filename());
        fileUploadRulesService.validateUpload(user, normalizedPath, filename, request.size());

        String objectKey = createBlobObjectKey();
        StoragePolicyCapabilities capabilities = contentAssetApi.resolveDefaultStoragePolicyCapabilities();
        if (capabilities != null && !capabilities.directUpload()) {
            return new InitiateUploadResponse(false, "", "POST", Map.of(), objectKey);
        }
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
        fileUploadRulesService.validateUpload(user, normalizedPath, filename, request.size());
        RegisteredContentFile savedFile = uploadCompletionApi.completeStoredBlob(new UploadCompletionCommand(
                user.getId(),
                normalizedPath,
                filename,
                objectKey,
                request.contentType(),
                request.size()
        ));
        return finalizeUploadedFile(user, normalizedPath, savedFile);
    }

    @Transactional
    public FileMetadataResponse mkdir(User user, String path) {
        String normalizedPath = normalizeDirectoryPath(path);
        FileMetadataResponse response = workspaceDirectoryApi.createDirectory(user.getId(), normalizedPath);
        String parentPath = extractParentPath(normalizedPath);
        touchDirectoryListings(user, parentPath);
        return response;
    }

    public PageResponse<FileMetadataResponse> list(User user, String path, int page, int size) {
        String normalizedPath = normalizeDirectoryPath(path);
        return fileListDirectoryCacheService.getOrLoad(
                user.getId(),
                normalizedPath,
                page,
                size,
                () -> workspaceDirectoryApi.loadDirectoryPage(user.getId(), normalizedPath, page, size)
        );
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

    @Override
    @Transactional
    public void ensureDefaultDirectories(WorkspaceUserContext user) {
        ensureDefaultDirectories(toWorkspaceUser(user));
    }

    @Override
    @Transactional
    public FileMetadataResponse importExternalFile(WorkspaceUserContext user,
                                                   String path,
                                                   String filename,
                                                   String contentType,
                                                   long size,
                                                   byte[] content) {
        return importExternalFile(toWorkspaceUser(user), path, filename, contentType, size, content);
    }

    @Override
    @Transactional
    public void importExternalFilesAtomically(WorkspaceUserContext user,
                                              List<String> directories,
                                              List<WorkspaceExternalFileImport> files,
                                              WorkspaceExternalImportProgressListener progressListener) {
        importExternalFilesAtomically(
                toWorkspaceUser(user),
                directories,
                files.stream()
                        .map(file -> new ExternalFileImport(file.path(), file.filename(), file.contentType(), file.content()))
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
        ArchiveSourceSummary summary = summarizeArchiveSource(source);
        return new WorkspaceArchiveSummary(summary.fileCount(), summary.directoryCount());
    }

    @Override
    public byte[] buildArchiveBytes(Long userId, Long fileId, WorkspaceArchiveBuildProgressListener progressListener) {
        StoredFile source = getOwnedActiveFile(toWorkspaceUser(userId), fileId, "归档");
        return buildArchiveBytes(
                source,
                progressListener == null
                        ? null
                        : progress -> progressListener.onProgress(new WorkspaceArchiveBuildProgress(
                        progress.processedFileCount(),
                        progress.totalFileCount(),
                        progress.processedDirectoryCount(),
                        progress.totalDirectoryCount()
                ))
        );
    }

    @Override
    public WorkspaceZipArchive readZipCompatibleArchive(Long userId, Long fileId) {
        StoredFile source = getOwnedActiveFile(toWorkspaceUser(userId), fileId, "解压");
        ZipCompatibleArchive archive = readZipCompatibleArchive(source);
        return new WorkspaceZipArchive(
                archive.entries().stream()
                        .map(entry -> new WorkspaceZipArchiveEntry(entry.relativePath(), entry.directory(), entry.content()))
                        .toList(),
                archive.commonRootDirectoryName()
        );
    }

    @Transactional
    public void ensureDefaultDirectories(User user) {
        boolean createdAny = false;
        for (String directoryName : DEFAULT_DIRECTORIES) {
            if (workspaceNodeRulesService.existsNodeName(user.getId(), "/", directoryName)) {
                continue;
            }

            String logicalPath = "/" + directoryName;
            fileContentStorage.ensureDirectory(user.getId(), logicalPath);

            StoredFile storedFile = new StoredFile();
            storedFile.setUser(user);
            storedFile.setFilename(directoryName);
            storedFile.setPath("/");
            storedFile.setLegacyStorageName(directoryName);
            storedFile.setContentType("directory");
            storedFile.setSize(0L);
            storedFile.setDirectory(true);
            storedFileRepository.save(storedFile);
            createdAny = true;
        }
        if (createdAny) {
            touchDirectoryListings(user, "/");
        }
    }

    @Transactional
    public void delete(User user, Long fileId) {
        WorkspaceLifecycleResult result = workspaceLifecycleApi.recycle(user.getId(), fileId);
        if (!result.affectedPaths().isEmpty()) {
            touchDirectoryListings(user, result.affectedPaths().toArray(String[]::new));
        }
        recordFileEvent(user, FileEventType.DELETED, result.file(), result.fromPath(), result.toPath());
    }

    @Transactional
    public FileMetadataResponse restoreFromRecycleBin(User user, Long fileId) {
        return distributedLockGateway.executeWithLock(
                "files:recycle-restore:" + fileId,
                Duration.ofSeconds(120),
                () -> {
                    WorkspaceLifecycleResult result = workspaceLifecycleApi.restore(
                            user.getId(),
                            fileId,
                            additionalBytes -> fileUploadRulesService.ensureWithinStorageQuota(user, additionalBytes)
                    );
                    if (!result.affectedPaths().isEmpty()) {
                        touchDirectoryListings(user, result.affectedPaths().toArray(String[]::new));
                    }
                    recordFileEvent(user, FileEventType.RESTORED, result.file(), result.fromPath(), result.toPath());
                    return result.file();
                }
        );
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    @Transactional
    public void pruneExpiredRecycleBinItems() {
        List<StoredFile> expiredItems = storedFileRepository.findByDeletedAtBefore(LocalDateTime.now().minusDays(RECYCLE_BIN_RETENTION_DAYS));
        if (expiredItems.isEmpty()) {
            return;
        }

        List<FileBlob> blobsToDelete = contentBlobLifecycleService.collectBlobsToDelete(
                expiredItems.stream().filter(item -> !item.isDirectory()).toList()
        );
        storedFileRepository.deleteAll(expiredItems);
        contentBlobLifecycleService.deleteBlobs(blobsToDelete);
    }

    @Transactional
    public FileMetadataResponse rename(User user, Long fileId, String nextFilename) {
        String sanitizedFilename = normalizeLeafName(nextFilename);
        WorkspaceMutationResult result = workspaceMutationApi.rename(user.getId(), fileId, sanitizedFilename);
        if (!result.affectedPaths().isEmpty()) {
            touchDirectoryListings(user, result.affectedPaths().toArray(String[]::new));
        }
        if (!result.fromPath().equals(result.toPath())) {
            recordFileEvent(user, FileEventType.RENAMED, result.file(), result.fromPath(), result.toPath());
        }
        return result.file();
    }

    @Transactional
    public FileMetadataResponse move(User user, Long fileId, String nextPath) {
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        WorkspaceMutationResult result = workspaceMutationApi.move(user.getId(), fileId, normalizedTargetPath);
        if (!result.affectedPaths().isEmpty()) {
            touchDirectoryListings(user, result.affectedPaths().toArray(String[]::new));
        }
        if (!result.fromPath().equals(result.toPath())) {
            recordFileEvent(user, FileEventType.MOVED, result.file(), result.fromPath(), result.toPath());
        }
        return result.file();
    }

    @Transactional
    public FileMetadataResponse copy(User user, Long fileId, String nextPath) {
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        WorkspaceLifecycleResult result = workspaceLifecycleApi.copy(
                user.getId(),
                fileId,
                normalizedTargetPath,
                additionalBytes -> fileUploadRulesService.ensureWithinStorageQuota(user, additionalBytes)
        );
        if (!result.affectedPaths().isEmpty()) {
            touchDirectoryListings(user, result.affectedPaths().toArray(String[]::new));
        }
        return result.file();
    }

    public WorkspaceDownloadResult download(User user, Long fileId) {
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
                    contentBlobLifecycleService.getRequiredBlob(storedFile).getObjectKey(),
                    storedFile.getFilename()
            ));
        }

        recordWorkspaceDownloadTraffic(storedFile.getSize());
        return WorkspaceDownloadResult.inline(
                storedFile.getFilename(),
                storedFile.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : storedFile.getContentType(),
                fileContentStorage.readBlob(contentBlobLifecycleService.getRequiredBlob(storedFile).getObjectKey())
        );
    }

    public DownloadUrlResponse getDownloadUrl(User user, Long fileId) {
        StoredFile storedFile = getOwnedActiveFile(user, fileId, "下载");
        if (storedFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录不支持下载");
        }

        if (shouldUsePublicPackageDownload(storedFile)) {
            return new DownloadUrlResponse(buildPublicPackageDownloadUrl(storedFile));
        }

        if (fileContentStorage.supportsDirectDownload()) {
            return new DownloadUrlResponse(fileContentStorage.createBlobDownloadUrl(
                    contentBlobLifecycleService.getRequiredBlob(storedFile).getObjectKey(),
                    storedFile.getFilename()
            ));
        }

        return new DownloadUrlResponse("/api/files/download/" + storedFile.getId());
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
        fileUploadRulesService.validateUpload(recipient, normalizedPath, normalizedFilename, size);
        ensureDirectoryHierarchy(recipient, normalizedPath);
        String objectKey = createBlobObjectKey();
        return contentBlobLifecycleService.executeAfterBlobStored(objectKey, () -> {
            fileContentStorage.storeBlob(objectKey, contentType, content);
            FileBlob blob = contentBlobLifecycleService.createAndSaveBlob(objectKey, contentType, size);

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
            for (ExternalFileImport file : normalizedFiles) {
                storeExternalImportFile(recipient, file, writtenBlobObjectKeys);
                processedFileCount += 1;
                reportExternalImportProgress(progressListener, processedFileCount, totalFileCount,
                        processedDirectoryCount, totalDirectoryCount);
            }
        } catch (RuntimeException ex) {
            contentBlobLifecycleService.cleanupWrittenBlobs(writtenBlobObjectKeys, ex);
            throw ex;
        }
    }

    private WorkspaceDownloadResult downloadDirectory(User user, StoredFile directory) {
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
        byte[] archiveBytes = fileContentStorage.readBlob(contentBlobLifecycleService.getRequiredBlob(source).getObjectKey());
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
        FileBlob blob = contentBlobLifecycleService.getRequiredBlob(storedFile);
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
        RegisteredContentFile savedFile = contentRegistrationApi.registerBlob(
                new ContentRegistrationCommand(
                        user.getId(),
                        normalizedPath,
                        filename,
                        contentType,
                        size,
                        new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize())
                )
        );
        return finalizeUploadedFile(user, normalizedPath, savedFile);
    }

    private FileMetadataResponse finalizeUploadedFile(User user,
                                                      String normalizedPath,
                                                      RegisteredContentFile savedFile) {
        touchDirectoryListings(user, normalizedPath);
        publishMediaMetadataTrigger(user, savedFile);
        recordFileEvent(user, FileEventType.CREATED, savedFile, null, buildLogicalPath(savedFile.path(), savedFile.filename()));
        return toResponse(savedFile);
    }

    private void publishMediaMetadataTrigger(User user, RegisteredContentFile storedFile) {
        if (backgroundTaskLifecycleApi == null) {
            return;
        }
        backgroundTaskLifecycleApi.createQueuedAutoMediaMetadataTask(user.getId(), storedFile.id(), null);
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

    private void validateUpload(User user, String normalizedPath, String filename, long size) {
        if (fileUploadRulesService != null) {
            fileUploadRulesService.validateUpload(user, normalizedPath, filename, size);
            return;
        }
        long effectiveMaxUploadSize = Math.min(maxFileSize, user.getMaxUploadSizeBytes());
        long policyMaxSizeBytes = 0L;
        StoragePolicyCapabilities capabilities = null;
        if (storagePolicyQuery != null) {
            var defaultPolicySnapshot = storagePolicyQuery.readDefaultPolicySnapshot();
            policyMaxSizeBytes = defaultPolicySnapshot.policyMaxSizeBytes();
            capabilities = defaultPolicySnapshot.capabilities();
        }
        if (policyMaxSizeBytes > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, policyMaxSizeBytes);
        }
        if (capabilities != null && capabilities.maxObjectSize() > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, capabilities.maxObjectSize());
        }
        if (size > effectiveMaxUploadSize) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件大小超出限制");
        }
        workspaceNodeRulesService.ensureNodeNameAvailable(user.getId(), normalizedPath, filename, "同目录下文件已存在");
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
        fileUploadRulesService.ensureWithinStorageQuota(recipient, files.stream().mapToLong(ExternalFileImport::size).sum());

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
            workspaceNodeRulesService.ensureNodeNameAvailable(recipient.getId(), parentPath, directoryName, "解压目标已存在");
        }

        for (ExternalFileImport file : files) {
            String logicalPath = buildTargetLogicalPath(file.path(), file.filename());
            if (plannedTargets.contains(logicalPath) || !plannedTargets.add(logicalPath)) {
                throw new BusinessException(ErrorCode.UNKNOWN, "解压目标已存在");
            }
            workspaceNodeRulesService.ensureNodeNameAvailable(recipient.getId(), file.path(), file.filename(), "同目录下文件已存在");
        }
    }

    private void ensureWithinStorageQuota(User user, long additionalBytes) {
        if (fileUploadRulesService != null) {
            fileUploadRulesService.ensureWithinStorageQuota(user, additionalBytes);
            return;
        }
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
        workspaceNodeRulesService.ensureDirectoryHierarchy(user, normalizedPath);
    }

    private void storeExternalImportFile(User recipient,
                                         ExternalFileImport file,
                                         List<String> writtenBlobObjectKeys) {
        fileUploadRulesService.validateUpload(recipient, file.path(), file.filename(), file.size());
        ensureDirectoryHierarchy(recipient, file.path());
        String objectKey = createBlobObjectKey();
        writtenBlobObjectKeys.add(objectKey);
        fileContentStorage.storeBlob(objectKey, file.contentType(), file.content());
        FileBlob blob = contentBlobLifecycleService.createAndSaveBlob(objectKey, file.contentType(), file.size());
        saveFileMetadata(
                recipient,
                file.path(),
                file.filename(),
                file.contentType(),
                file.size(),
                blob
        );
    }

    private String requireRecycleOriginalPath(StoredFile storedFile) {
        if (!StringUtils.hasText(storedFile.getRecycleOriginalPath())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "回收站文件不存在");
        }
        return storedFile.getRecycleOriginalPath();
    }

    private String normalizeUploadFilename(String originalFilename) {
        return workspaceNodeRulesService.normalizeUploadFilename(originalFilename);
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

    private FileMetadataResponse toResponse(RegisteredContentFile storedFile) {
        return new FileMetadataResponse(
                storedFile.id(),
                storedFile.filename(),
                storedFile.path(),
                storedFile.size(),
                storedFile.contentType(),
                storedFile.directory(),
                storedFile.createdAt()
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

    private String buildLogicalPath(String path, String filename) {
        return "/".equals(path)
                ? "/" + filename
                : path + "/" + filename;
    }

    private User toWorkspaceUser(WorkspaceUserContext user) {
        User workspaceUser = toWorkspaceUser(user.userId());
        workspaceUser.setStorageQuotaBytes(user.storageQuotaBytes() == null ? 0L : user.storageQuotaBytes());
        workspaceUser.setMaxUploadSizeBytes(user.maxUploadSizeBytes() == null ? maxFileSize : user.maxUploadSizeBytes());
        return workspaceUser;
    }

    private User toWorkspaceUser(Long userId) {
        User workspaceUser = new User();
        workspaceUser.setId(userId);
        workspaceUser.setStorageQuotaBytes(Long.MAX_VALUE);
        workspaceUser.setMaxUploadSizeBytes(maxFileSize);
        return workspaceUser;
    }

    private String buildTargetLogicalPath(String normalizedTargetPath, String filename) {
        return workspaceNodeRulesService.buildTargetLogicalPath(normalizedTargetPath, filename);
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
                fileContentStorage.readBlob(contentBlobLifecycleService.getRequiredBlob(file).getObjectKey()));
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
        if (fileEventApi == null || user == null || user.getId() == null || storedFile == null || storedFile.getId() == null) {
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
        fileEventApi.record(new FileEventRecordCommand(
                user.getId(),
                eventType,
                storedFile.getId(),
                fromPath,
                toPath,
                null,
                payload
        ));
    }

    private void recordFileEvent(User user,
                                 FileEventType eventType,
                                 FileMetadataResponse storedFile,
                                 String fromPath,
                                 String toPath) {
        if (fileEventApi == null || user == null || user.getId() == null || storedFile == null || storedFile.id() == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", eventType.name());
        payload.put("fileId", storedFile.id());
        payload.put("filename", storedFile.filename());
        payload.put("path", storedFile.path());
        payload.put("directory", storedFile.directory());
        payload.put("contentType", storedFile.contentType());
        payload.put("size", storedFile.size());
        if (fromPath != null) {
            payload.put("fromPath", fromPath);
        }
        if (toPath != null) {
            payload.put("toPath", toPath);
        }
        fileEventApi.record(new FileEventRecordCommand(
                user.getId(),
                eventType,
                storedFile.id(),
                fromPath,
                toPath,
                null,
                payload
        ));
    }

    private void recordFileEvent(User user,
                                 FileEventType eventType,
                                 RegisteredContentFile storedFile,
                                 String fromPath,
                                 String toPath) {
        if (fileEventApi == null || user == null || user.getId() == null || storedFile == null || storedFile.id() == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", eventType.name());
        payload.put("fileId", storedFile.id());
        payload.put("filename", storedFile.filename());
        payload.put("path", storedFile.path());
        payload.put("directory", storedFile.directory());
        payload.put("contentType", storedFile.contentType());
        payload.put("size", storedFile.size());
        if (fromPath != null) {
            payload.put("fromPath", fromPath);
        }
        if (toPath != null) {
            payload.put("toPath", toPath);
        }
        fileEventApi.record(new FileEventRecordCommand(
                user.getId(),
                eventType,
                storedFile.id(),
                fromPath,
                toPath,
                null,
                payload
        ));
    }

    private void touchDirectoryListings(User user, String... paths) {
        if (user == null || user.getId() == null || paths == null || paths.length == 0) {
            return;
        }

        List<String> affectedPaths = new ArrayList<>();
        for (String path : paths) {
            if (StringUtils.hasText(path)) {
                affectedPaths.add(normalizeDirectoryPath(path));
            }
        }
        if (affectedPaths.isEmpty()) {
            return;
        }

        fileListDirectoryCacheService.touchDirectories(user.getId(), affectedPaths);
    }

    private String normalizeLeafName(String filename) {
        return workspaceNodeRulesService.normalizeLeafName(filename);
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

    private FileMetadataResponse importReferencedBlob(User recipient,
                                                      String path,
                                                      String filename,
                                                      String contentType,
                                                      long size,
                                                      FileBlob blob) {
        String normalizedPath = normalizeDirectoryPath(path);
        String normalizedFilename = normalizeLeafName(filename);
        fileUploadRulesService.validateUpload(recipient, normalizedPath, normalizedFilename, size);
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

    public static record ZipCompatibleArchive(List<ZipCompatibleArchiveEntry> entries, String commonRootDirectoryName) {
    }

    public static record ZipCompatibleArchiveEntry(String relativePath, boolean directory, byte[] content) {
    }

    public static record ExternalFileImport(String path, String filename, String contentType, byte[] content) {
        public long size() {
            return content == null ? 0L : content.length;
        }
    }

    public static record ArchiveSourceSummary(int fileCount, int directoryCount) {
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
