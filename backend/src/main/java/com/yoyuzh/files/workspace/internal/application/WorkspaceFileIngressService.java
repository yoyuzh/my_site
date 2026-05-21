package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.api.ContentPrimaryEntityApi;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.ContentBlobStateView;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.api.PreparedUpload;
import com.yoyuzh.files.upload.api.CompleteUploadRequest;
import com.yoyuzh.files.upload.api.InitiateUploadRequest;
import com.yoyuzh.files.upload.api.InitiateUploadResponse;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.upload.api.UploadCompletionCommand;
import com.yoyuzh.files.workspace.api.WorkspaceDeferredBlobFinalizeApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceDeferredUploadStagingApi;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkspaceFileIngressService implements WorkspaceDeferredUploadStagingApi {

    private final FileContentStorage fileContentStorage;
    private final ContentAssetApi contentAssetApi;
    private final ContentPrimaryEntityApi contentPrimaryEntityApi;
    private final ContentBlobQueryApi contentBlobQueryApi;
    private final ContentRegistrationApi contentRegistrationApi;
    private final ContentBlobRegistrationApi contentBlobRegistrationApi;
    private final UploadCompletionApi uploadCompletionApi;
    private final ContentBlobLifecycleApi contentBlobLifecycleApi;
    private final StoredFileRepository storedFileRepository;
    private final FileUploadRulesService fileUploadRulesService;
    private final WorkspaceNodeRulesService workspaceNodeRulesService;
    private final WorkspaceRequestProbe workspaceRequestProbe;
    private final TransactionOperations transactionOperations;
    private final Path pendingBlobTempDir;

    @Autowired
    public WorkspaceFileIngressService(FileContentStorage fileContentStorage,
                                       ContentAssetApi contentAssetApi,
                                       ContentBlobQueryApi contentBlobQueryApi,
                                       ContentRegistrationApi contentRegistrationApi,
                                       ContentBlobRegistrationApi contentBlobRegistrationApi,
                                       UploadCompletionApi uploadCompletionApi,
                                       ContentBlobLifecycleApi contentBlobLifecycleApi,
                                       StoredFileRepository storedFileRepository,
                                       FileUploadRulesService fileUploadRulesService,
                                       WorkspaceNodeRulesService workspaceNodeRulesService,
                                       WorkspaceRequestProbe workspaceRequestProbe,
                                       TransactionOperations transactionOperations,
                                       StorageRuntimeProperties storageRuntimeProperties) {
        this.fileContentStorage = fileContentStorage;
        this.contentAssetApi = contentAssetApi;
        this.contentPrimaryEntityApi = contentAssetApi;
        this.contentBlobQueryApi = contentBlobQueryApi;
        this.contentRegistrationApi = contentRegistrationApi;
        this.contentBlobRegistrationApi = contentBlobRegistrationApi;
        this.uploadCompletionApi = uploadCompletionApi;
        this.contentBlobLifecycleApi = contentBlobLifecycleApi;
        this.storedFileRepository = storedFileRepository;
        this.fileUploadRulesService = fileUploadRulesService;
        this.workspaceNodeRulesService = workspaceNodeRulesService;
        this.workspaceRequestProbe = workspaceRequestProbe == null
                ? WorkspaceRequestProbe.disabled()
                : workspaceRequestProbe;
        this.transactionOperations = transactionOperations;
        this.pendingBlobTempDir = initializePendingBlobTempDir(storageRuntimeProperties);
    }

    public WorkspaceFileIngressService(FileContentStorage fileContentStorage,
                                       ContentAssetApi contentAssetApi,
                                       ContentBlobQueryApi contentBlobQueryApi,
                                       ContentRegistrationApi contentRegistrationApi,
                                       ContentBlobRegistrationApi contentBlobRegistrationApi,
                                       UploadCompletionApi uploadCompletionApi,
                                       ContentBlobLifecycleApi contentBlobLifecycleApi,
                                       StoredFileRepository storedFileRepository,
                                       FileUploadRulesService fileUploadRulesService,
                                       WorkspaceNodeRulesService workspaceNodeRulesService) {
        this(
                fileContentStorage,
                contentAssetApi,
                contentBlobQueryApi,
                contentRegistrationApi,
                contentBlobRegistrationApi,
                uploadCompletionApi,
                contentBlobLifecycleApi,
                storedFileRepository,
                fileUploadRulesService,
                workspaceNodeRulesService,
                WorkspaceRequestProbe.disabled(),
                null,
                defaultStorageRuntimeProperties()
        );
    }

    public WorkspaceFileIngressService(FileContentStorage fileContentStorage,
                                       ContentAssetApi contentAssetApi,
                                       ContentBlobQueryApi contentBlobQueryApi,
                                       ContentRegistrationApi contentRegistrationApi,
                                       ContentBlobRegistrationApi contentBlobRegistrationApi,
                                       UploadCompletionApi uploadCompletionApi,
                                       ContentBlobLifecycleApi contentBlobLifecycleApi,
                                       StoredFileRepository storedFileRepository,
                                       FileUploadRulesService fileUploadRulesService,
                                       WorkspaceNodeRulesService workspaceNodeRulesService,
                                       WorkspaceRequestProbe workspaceRequestProbe) {
        this(
                fileContentStorage,
                contentAssetApi,
                contentBlobQueryApi,
                contentRegistrationApi,
                contentBlobRegistrationApi,
                uploadCompletionApi,
                contentBlobLifecycleApi,
                storedFileRepository,
                fileUploadRulesService,
                workspaceNodeRulesService,
                workspaceRequestProbe,
                null,
                defaultStorageRuntimeProperties()
        );
    }

    public CreatedFile upload(WorkspaceUserContext user,
                              String path,
                              MultipartFile multipartFile,
                              ContentTypeResolver contentTypeResolver) {
        String normalizedPath = workspaceRequestProbe.measure("ingress.normalizePath", () -> normalizeDirectoryPath(path));
        String filename = workspaceRequestProbe.measure(
                "ingress.normalizeFilename",
                () -> normalizeUploadFilename(multipartFile.getOriginalFilename())
        );
        String contentType = workspaceRequestProbe.measure(
                "ingress.resolveContentType",
                () -> contentTypeResolver.resolve(filename, multipartFile.getContentType())
        );
        workspaceRequestProbe.putMetadata("normalizedPath", normalizedPath);
        workspaceRequestProbe.putMetadata("filename", filename);
        workspaceRequestProbe.putMetadata("size", multipartFile.getSize());
        workspaceRequestProbe.measure(
                "ingress.validateUpload",
                () -> fileUploadRulesService.validateUpload(user, normalizedPath, filename, multipartFile.getSize())
        );
        workspaceRequestProbe.measure("ingress.ensureDirectoryHierarchy", () -> ensureDirectoryHierarchy(user, normalizedPath));

        String objectKey = createBlobObjectKey();
        RegisteredContentFile savedFile = workspaceRequestProbe.measure("ingress.storeBlobAndRegister", () ->
                contentBlobLifecycleApi.executeAfterBlobStored(objectKey, () -> {
                    workspaceRequestProbe.measure("ingress.uploadBlob", () -> fileContentStorage.uploadBlob(objectKey, multipartFile));
                    ContentBlobReference blob = workspaceRequestProbe.measure(
                            "ingress.registerStoredBlob",
                            () -> contentBlobRegistrationApi.registerStoredBlob(objectKey, contentType, multipartFile.getSize())
                    );
                    return workspaceRequestProbe.measure(
                            "ingress.registerBlob",
                            () -> registerBlob(user, normalizedPath, filename, contentType, multipartFile.getSize(), blob)
                    );
                })
        );
        return new CreatedFile(normalizedPath, savedFile);
    }

    public InitiateUploadResponse initiateUpload(WorkspaceUserContext user,
                                                 InitiateUploadRequest request,
                                                 ContentTypeResolver contentTypeResolver) {
        String normalizedPath = workspaceRequestProbe.measure("ingress.normalizePath", () -> normalizeDirectoryPath(request.path()));
        String filename = workspaceRequestProbe.measure("ingress.normalizeFilename", () -> normalizeLeafName(request.filename()));
        String contentType = workspaceRequestProbe.measure(
                "ingress.resolveContentType",
                () -> contentTypeResolver.resolve(filename, request.contentType())
        );
        workspaceRequestProbe.putMetadata("normalizedPath", normalizedPath);
        workspaceRequestProbe.putMetadata("filename", filename);
        workspaceRequestProbe.putMetadata("size", request.size());
        workspaceRequestProbe.measure(
                "ingress.validateUpload",
                () -> fileUploadRulesService.validateUpload(user, normalizedPath, filename, request.size())
        );

        String objectKey = createBlobObjectKey();
        StoragePolicyCapabilities capabilities = workspaceRequestProbe.measure(
                "ingress.resolveStorageCapabilities",
                contentAssetApi::resolveDefaultStoragePolicyCapabilities
        );
        if (capabilities != null && !capabilities.directUpload()) {
            return new InitiateUploadResponse(false, "", "POST", Map.of(), objectKey);
        }
        PreparedUpload preparedUpload = workspaceRequestProbe.measure(
                "ingress.prepareBlobUpload",
                () -> fileContentStorage.prepareBlobUpload(
                        normalizedPath,
                        filename,
                        objectKey,
                        contentType,
                        request.size()
                )
        );

        return new InitiateUploadResponse(
                preparedUpload.direct(),
                preparedUpload.uploadUrl(),
                preparedUpload.method(),
                preparedUpload.headers(),
                preparedUpload.storageName()
        );
    }

    public CreatedFile completeUpload(WorkspaceUserContext user,
                                      CompleteUploadRequest request,
                                      ContentTypeResolver contentTypeResolver) {
        String normalizedPath = workspaceRequestProbe.measure("ingress.normalizePath", () -> normalizeDirectoryPath(request.path()));
        String filename = workspaceRequestProbe.measure("ingress.normalizeFilename", () -> normalizeLeafName(request.filename()));
        String objectKey = workspaceRequestProbe.measure("ingress.normalizeBlobObjectKey", () -> normalizeBlobObjectKey(request.storageName()));
        String contentType = workspaceRequestProbe.measure(
                "ingress.resolveContentType",
                () -> contentTypeResolver.resolve(filename, request.contentType())
        );
        workspaceRequestProbe.putMetadata("normalizedPath", normalizedPath);
        workspaceRequestProbe.putMetadata("filename", filename);
        workspaceRequestProbe.putMetadata("size", request.size());
        workspaceRequestProbe.measure(
                "ingress.validateUpload",
                () -> fileUploadRulesService.validateUpload(user, normalizedPath, filename, request.size())
        );
        RegisteredContentFile savedFile = workspaceRequestProbe.measure(
                "ingress.completeStoredBlob",
                () -> uploadCompletionApi.completeStoredBlob(new UploadCompletionCommand(
                        user.userId(),
                        normalizedPath,
                        filename,
                        objectKey,
                        contentType,
                        request.size()
                ))
        );
        return new CreatedFile(normalizedPath, savedFile);
    }

    public CreatedFile importExternalFile(WorkspaceUserContext recipient,
                                          String path,
                                          String filename,
                                          String contentType,
                                          long size,
                                          byte[] content) {
        List<String> writtenBlobObjectKeys = new ArrayList<>();
        try {
            return importExternalFile(recipient, path, filename, contentType, size, new ByteArrayInputStream(content == null ? new byte[0] : content), writtenBlobObjectKeys);
        } catch (IOException ex) {
            cleanupWrittenBlobs(writtenBlobObjectKeys, new IllegalStateException("failed to import external file content", ex));
            throw new IllegalStateException("failed to import external file content", ex);
        }
    }

    CreatedFile importExternalFile(WorkspaceUserContext recipient,
                                   String path,
                                   String filename,
                                   String contentType,
                                   long size,
                                   java.io.InputStream contentStream,
                                   List<String> writtenBlobObjectKeys) throws IOException {
        String normalizedPath = workspaceRequestProbe.measure("ingress.normalizePath", () -> normalizeDirectoryPath(path));
        String normalizedFilename = workspaceRequestProbe.measure("ingress.normalizeFilename", () -> normalizeLeafName(filename));
        workspaceRequestProbe.measure(
                "ingress.validateUpload",
                () -> fileUploadRulesService.validateUpload(recipient, normalizedPath, normalizedFilename, size)
        );
        workspaceRequestProbe.measure("ingress.ensureDirectoryHierarchy", () -> ensureDirectoryHierarchy(recipient, normalizedPath));
        String objectKey = createBlobObjectKey();
        if (writtenBlobObjectKeys != null) {
            writtenBlobObjectKeys.add(objectKey);
        }
        RegisteredContentFile savedFile = workspaceRequestProbe.measure("ingress.storeBlobAndRegister", () ->
                contentBlobLifecycleApi.executeAfterBlobStored(objectKey, () -> {
                    workspaceRequestProbe.measure("ingress.storeBlob", () -> fileContentStorage.storeBlob(objectKey, contentType, contentStream, size));
                    ContentBlobReference blob = workspaceRequestProbe.measure(
                            "ingress.registerStoredBlob",
                            () -> contentBlobRegistrationApi.registerStoredBlob(objectKey, contentType, size)
                    );
                    return workspaceRequestProbe.measure(
                            "ingress.registerBlob",
                            () -> registerBlob(recipient, normalizedPath, normalizedFilename, contentType, size, blob)
                    );
                })
        );
        return new CreatedFile(normalizedPath, savedFile);
    }

    public CreatedFile storeWebDavFile(WorkspaceUserContext recipient,
                                       String path,
                                       String filename,
                                       String contentType,
                                       long size,
                                       java.io.InputStream contentStream) {
        String normalizedPath = workspaceRequestProbe.measure("ingress.normalizePath", () -> normalizeDirectoryPath(path));
        String normalizedFilename = workspaceRequestProbe.measure("ingress.normalizeFilename", () -> normalizeLeafName(filename));
        workspaceRequestProbe.measure(
                "ingress.validateUpload",
                () -> fileUploadRulesService.validateUpload(recipient, normalizedPath, normalizedFilename, size)
        );
        workspaceRequestProbe.measure("ingress.ensureDirectoryHierarchy", () -> ensureDirectoryHierarchy(recipient, normalizedPath));
        String objectKey = createBlobObjectKey();
        RegisteredContentFile savedFile = workspaceRequestProbe.measure("ingress.storeWebDavBlobAndRegister", () ->
                contentBlobLifecycleApi.executeAfterBlobStored(objectKey, () -> {
                    workspaceRequestProbe.measure("ingress.storeBlob", () -> fileContentStorage.storeBlob(objectKey, contentType, contentStream, size));
                    return executeInShortTransaction(() -> {
                        ContentBlobReference blob = workspaceRequestProbe.measure(
                                "ingress.registerStoredBlob",
                                () -> contentBlobRegistrationApi.registerStoredBlob(objectKey, contentType, size)
                        );
                        return workspaceRequestProbe.measure(
                                "ingress.registerBlob",
                                () -> registerBlob(recipient, normalizedPath, normalizedFilename, contentType, size, blob)
                        );
                    });
                })
        );
        return new CreatedFile(normalizedPath, savedFile);
    }

    public List<CreatedFile> storeExternalFiles(WorkspaceUserContext recipient,
                                                List<FileService.ExternalFileImport> files,
                                                List<String> writtenBlobObjectKeys) {
        List<CreatedFile> createdFiles = new ArrayList<>();
        for (FileService.ExternalFileImport file : files) {
            try (java.io.InputStream contentStream = file.openStream()) {
                createdFiles.add(importExternalFile(
                        recipient,
                        file.path(),
                        file.filename(),
                        file.contentType(),
                        file.size(),
                        contentStream,
                        writtenBlobObjectKeys
                ));
            } catch (IOException ex) {
                throw new IllegalStateException("failed to import external file content", ex);
            }
        }
        return createdFiles;
    }

    public boolean supportsDeferredBlobUpload() {
        return fileContentStorage.supportsDeferredBlobUpload();
    }

    @Override
    public DeferredCreateStage prepareDeferredCreate(WorkspaceUserContext recipient,
                                                     String path,
                                                     String filename,
                                                     String contentType,
                                                     long size,
                                                     InputStream contentStream) throws IOException {
        String normalizedPath = workspaceRequestProbe.measure("ingress.normalizePath", () -> normalizeDirectoryPath(path));
        String normalizedFilename = workspaceRequestProbe.measure("ingress.normalizeFilename", () -> normalizeLeafName(filename));
        workspaceRequestProbe.measure(
                "ingress.validateUpload",
                () -> fileUploadRulesService.validateUpload(recipient, normalizedPath, normalizedFilename, size)
        );
        workspaceRequestProbe.measure("ingress.ensureDirectoryHierarchy", () -> ensureDirectoryHierarchy(recipient, normalizedPath));
        Path tempFile = workspaceRequestProbe.measureIo("ingress.writePendingBlobTempFile", () -> writePendingBlobTempFile(contentStream));
        registerRollbackTempFileCleanup(tempFile.toString());
        Long blobId = null;
        try {
            String objectKey = workspaceRequestProbe.measure("ingress.createBlobObjectKey", this::createBlobObjectKey);
            ContentBlobReference blob = workspaceRequestProbe.measure(
                    "ingress.registerPendingBlob",
                    () -> contentBlobRegistrationApi.registerPendingBlob(objectKey, contentType, size, tempFile.toString())
            );
            blobId = blob.blobId();
            RegisteredContentFile savedFile = workspaceRequestProbe.measure(
                    "ingress.registerBlob",
                    () -> registerBlob(recipient, normalizedPath, normalizedFilename, contentType, size, blob)
            );
            return new DeferredCreateStage(
                    normalizedPath,
                    savedFile,
                    blob,
                    tempFile.toString(),
                    contentType
            );
        } catch (RuntimeException | Error ex) {
            cleanupFailedDeferredBlob(blobId, tempFile.toString());
            throw ex;
        }
    }

    @Override
    public DeferredReplaceStage prepareDeferredReplace(WorkspaceUserContext user,
                                                       Long fileId,
                                                       String contentType,
                                                       long size,
                                                       long previousSize,
                                                       InputStream contentStream) throws IOException {
        workspaceRequestProbe.measure("ingress.validateReplacement", () -> fileUploadRulesService.validateReplacement(user, previousSize, size));
        workspaceRequestProbe.measure("ingress.readExistingMetadata", () -> readFileMetadata(fileId, user.userId()));
        Path tempFile = workspaceRequestProbe.measureIo("ingress.writePendingBlobTempFile", () -> writePendingBlobTempFile(contentStream));
        registerRollbackTempFileCleanup(tempFile.toString());
        Long blobId = null;
        try {
            String objectKey = workspaceRequestProbe.measure("ingress.createBlobObjectKey", this::createBlobObjectKey);
            ContentBlobReference blob = workspaceRequestProbe.measure(
                    "ingress.registerPendingBlob",
                    () -> contentBlobRegistrationApi.registerPendingBlob(objectKey, contentType, size, tempFile.toString())
            );
            blobId = blob.blobId();
            return new DeferredReplaceStage(
                    fileId,
                    blob,
                    tempFile.toString(),
                    contentType,
                    size,
                    resolveStoredBlobId(fileId, user.userId()),
                    resolveStoredPrimaryEntityId(fileId, user.userId())
            );
        } catch (RuntimeException | Error ex) {
            cleanupFailedDeferredBlob(blobId, tempFile.toString());
            throw ex;
        }
    }

    public WorkspaceDeferredBlobFinalizeApi.FinalizedReplacement finalizeDeferredReplace(Long userId,
                                                                                         Long fileId,
                                                                                         Long blobId,
                                                                                         String contentType,
                                                                                         long size) {
        PendingBlobDescriptor pendingBlob = requirePendingBlob(blobId);
        ContentBlobReference blob = new ContentBlobReference(blobId, pendingBlob.objectKey(), contentType, size);
        ContentPrimaryEntity primaryEntity = contentPrimaryEntityApi.createOrReferencePrimaryEntity(userId, blob);
        contentPrimaryEntityApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(fileId, primaryEntity.entityId()));
        return new WorkspaceDeferredBlobFinalizeApi.FinalizedReplacement(
                blob.blobId(),
                blob.objectKey(),
                primaryEntity.entityId(),
                contentType,
                size
        );
    }

    @Override
    public void attachDeferredBlobTask(Long blobId, Long uploadTaskId) {
        contentBlobRegistrationApi.attachUploadTask(blobId, uploadTaskId);
    }

    @Override
    public void cleanupFailedDeferredBlob(Long blobId, String localTempPath) {
        try {
            if (blobId != null) {
                contentBlobRegistrationApi.markBlobFailed(blobId);
            }
        } catch (RuntimeException ignored) {
        } finally {
            deletePendingTempFile(localTempPath);
        }
    }

    @Override
    public FileMetadataResponse readFileMetadata(Long fileId, Long userId) {
        return storedFileRepository.findDetailedByIdAndUserId(fileId, userId)
                .map(file -> new FileMetadataResponse(
                        file.getId(),
                        file.getFilename(),
                        file.getPath(),
                        file.getSize(),
                        file.getContentType(),
                        file.isDirectory(),
                        file.getCreatedAt(),
                        file.getUpdatedAt() == null ? file.getCreatedAt() : file.getUpdatedAt(),
                        file.getCustomEmoji(),
                        file.getFolderColor(),
                        false
                ))
                .orElseThrow(() -> new IllegalStateException("file metadata not found"));
    }

    private Long resolveStoredBlobId(Long fileId, Long userId) {
        return storedFileRepository.findDetailedByIdAndUserId(fileId, userId)
                .map(com.yoyuzh.files.workspace.internal.domain.StoredFile::getBlobId)
                .orElse(null);
    }

    private Long resolveStoredPrimaryEntityId(Long fileId, Long userId) {
        return storedFileRepository.findDetailedByIdAndUserId(fileId, userId)
                .map(com.yoyuzh.files.workspace.internal.domain.StoredFile::getPrimaryEntityId)
                .orElse(null);
    }

    public void deletePendingTempFile(String localTempPath) {
        if (localTempPath == null || localTempPath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(localTempPath));
        } catch (IOException ignored) {
        }
    }

    public ReplacementContent replaceFileContent(WorkspaceUserContext user,
                                                 Long fileId,
                                                 String contentType,
                                                 long size,
                                                 long previousSize,
                                                 java.io.InputStream contentStream) {
        fileUploadRulesService.validateReplacement(user, previousSize, size);
        String objectKey = createBlobObjectKey();
        try {
            return workspaceRequestProbe.measure("ingress.replaceContent", () ->
                    contentBlobLifecycleApi.executeAfterBlobStored(objectKey, () -> {
                        workspaceRequestProbe.measure("ingress.storeBlob", () -> fileContentStorage.storeBlob(objectKey, contentType, contentStream, size));
                        ContentBlobReference blob = workspaceRequestProbe.measure(
                                "ingress.registerStoredBlob",
                                () -> contentBlobRegistrationApi.registerStoredBlob(objectKey, contentType, size)
                        );
                        ContentPrimaryEntity primaryEntity = workspaceRequestProbe.measure(
                                "ingress.createPrimaryEntity",
                                () -> contentAssetApi.createOrReferencePrimaryEntity(user.userId(), blob)
                        );
                        workspaceRequestProbe.measure(
                                "ingress.savePrimaryRelation",
                                () -> contentAssetApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(fileId, primaryEntity.entityId()))
                        );
                        return new ReplacementContent(blob.blobId(), blob.objectKey(), primaryEntity.entityId());
                    })
            );
        } finally {
            try {
                contentStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    public ReplacementContent replaceWebDavFileContent(WorkspaceUserContext user,
                                                       com.yoyuzh.files.workspace.internal.domain.StoredFile existingFile,
                                                       String contentType,
                                                       long size,
                                                       long previousSize,
                                                       java.io.InputStream contentStream) {
        workspaceRequestProbe.measure("ingress.validateReplacement", () -> fileUploadRulesService.validateReplacement(user, previousSize, size));
        String objectKey = createBlobObjectKey();
        return workspaceRequestProbe.measure("ingress.replaceWebDavContent", () ->
                contentBlobLifecycleApi.executeAfterBlobStored(objectKey, () -> {
                    workspaceRequestProbe.measure("ingress.storeBlob", () -> fileContentStorage.storeBlob(objectKey, contentType, contentStream, size));
                    return executeInShortTransaction(() -> {
                        ContentBlobReference blob = workspaceRequestProbe.measure(
                                "ingress.registerStoredBlob",
                                () -> contentBlobRegistrationApi.registerStoredBlob(objectKey, contentType, size)
                        );
                        ContentPrimaryEntity primaryEntity = workspaceRequestProbe.measure(
                                "ingress.createPrimaryEntity",
                                () -> contentAssetApi.createOrReferencePrimaryEntity(user.userId(), blob)
                        );
                        workspaceRequestProbe.measure(
                                "ingress.savePrimaryRelation",
                                () -> contentAssetApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(existingFile.getId(), primaryEntity.entityId()))
                        );
                        existingFile.setBlobId(blob.blobId());
                        existingFile.setPrimaryEntityId(primaryEntity.entityId());
                        existingFile.setLegacyStorageName(blob.objectKey());
                        existingFile.setContentType(contentType);
                        existingFile.setSize(size);
                        workspaceRequestProbe.measure("ingress.saveReplacedWebDavFile", () -> storedFileRepository.save(existingFile));
                        return new ReplacementContent(blob.blobId(), blob.objectKey(), primaryEntity.entityId());
                    });
                })
        );
    }

    public void cleanupWrittenBlobs(List<String> writtenBlobObjectKeys, RuntimeException ex) {
        contentBlobLifecycleApi.cleanupWrittenBlobs(writtenBlobObjectKeys, ex);
    }

    private RegisteredContentFile registerBlob(WorkspaceUserContext user,
                                               String normalizedPath,
                                               String filename,
                                               String contentType,
                                               long size,
                                               ContentBlobReference blob) {
        return contentRegistrationApi.registerBlob(
                new ContentRegistrationCommand(
                        user.userId(),
                        normalizedPath,
                        filename,
                        contentType,
                        size,
                        blob
                )
        );
    }

    private <T> T executeInShortTransaction(java.util.function.Supplier<T> supplier) {
        if (transactionOperations == null || TransactionSynchronizationManager.isActualTransactionActive()) {
            return supplier.get();
        }
        return transactionOperations.execute(status -> supplier.get());
    }

    private void ensureDirectoryHierarchy(WorkspaceUserContext user, String normalizedPath) {
        workspaceNodeRulesService.ensureDirectoryHierarchy(user.userId(), normalizedPath);
    }

    private String normalizeDirectoryPath(String path) {
        return workspaceNodeRulesService.normalizeDirectoryPath(path);
    }

    private String normalizeUploadFilename(String originalFilename) {
        return workspaceNodeRulesService.normalizeUploadFilename(originalFilename);
    }

    private String normalizeLeafName(String filename) {
        return workspaceNodeRulesService.normalizeLeafName(filename);
    }

    private String normalizeBlobObjectKey(String objectKey) {
        String cleaned = org.springframework.util.StringUtils.cleanPath(objectKey == null ? "" : objectKey).trim().replace("\\", "/");
        if (!org.springframework.util.StringUtils.hasText(cleaned) || cleaned.contains("..") || cleaned.startsWith("/") || !cleaned.startsWith("blobs/")) {
            throw new com.yoyuzh.shared.kernel.BusinessException(com.yoyuzh.shared.kernel.ErrorCode.UNKNOWN, "上传对象标识不合法");
        }
        return cleaned;
    }

    private String createBlobObjectKey() {
        return "blobs/" + UUID.randomUUID();
    }

    private Path writePendingBlobTempFile(InputStream contentStream) throws IOException {
        Files.createDirectories(pendingBlobTempDir);
        Path tempFile = Files.createTempFile(pendingBlobTempDir, "pending-blob-", ".tmp");
        try (InputStream inputStream = contentStream) {
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private void registerRollbackTempFileCleanup(String localTempPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deletePendingTempFile(localTempPath);
                }
            }
        });
    }

    private PendingBlobDescriptor requirePendingBlob(Long blobId) {
        ContentBlobStateView state = contentBlobQueryApi.findBlobStateById(blobId)
                .orElseThrow(() -> new IllegalStateException("pending blob not found"));
        return new PendingBlobDescriptor(state.objectKey());
    }

    private Path initializePendingBlobTempDir(StorageRuntimeProperties storageRuntimeProperties) {
        String configured = storageRuntimeProperties == null ? null : storageRuntimeProperties.getPendingBlobTempDir();
        String resolved = (configured == null || configured.isBlank())
                ? System.getProperty("java.io.tmpdir") + "/yoyuzh-pending-blobs"
                : configured;
        return Path.of(resolved).toAbsolutePath().normalize();
    }

    private static StorageRuntimeProperties defaultStorageRuntimeProperties() {
        return new StorageRuntimeProperties() {
            @Override
            public String getProvider() {
                return "local";
            }

            @Override
            public Local getLocal() {
                return null;
            }

            @Override
            public S3 getS3() {
                return null;
            }

            @Override
            public Oss getOss() {
                return null;
            }

            @Override
            public WebDav getWebDav() {
                return null;
            }

            @Override
            public long getMaxFileSize() {
                return 0;
            }

            @Override
            public String getPendingBlobTempDir() {
                return System.getProperty("java.io.tmpdir") + "/yoyuzh-pending-blobs";
            }
        };
    }

    @FunctionalInterface
    public interface ContentTypeResolver {
        String resolve(String filename, String reportedContentType);
    }

    public record CreatedFile(String normalizedPath, RegisteredContentFile file) {
    }

    public record ReplacementContent(Long blobId, String objectKey, Long primaryEntityId) {
    }

    private record PendingBlobDescriptor(String objectKey) {
    }
}
