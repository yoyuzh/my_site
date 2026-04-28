package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.api.PreparedUpload;
import com.yoyuzh.files.upload.CompleteUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.upload.api.UploadCompletionCommand;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkspaceFileIngressService {

    private final FileContentStorage fileContentStorage;
    private final ContentAssetApi contentAssetApi;
    private final ContentRegistrationApi contentRegistrationApi;
    private final ContentBlobRegistrationApi contentBlobRegistrationApi;
    private final UploadCompletionApi uploadCompletionApi;
    private final ContentBlobLifecycleApi contentBlobLifecycleApi;
    private final FileUploadRulesService fileUploadRulesService;
    private final WorkspaceNodeRulesService workspaceNodeRulesService;

    public WorkspaceFileIngressService(FileContentStorage fileContentStorage,
                                       ContentAssetApi contentAssetApi,
                                       ContentRegistrationApi contentRegistrationApi,
                                       ContentBlobRegistrationApi contentBlobRegistrationApi,
                                       UploadCompletionApi uploadCompletionApi,
                                       ContentBlobLifecycleApi contentBlobLifecycleApi,
                                       FileUploadRulesService fileUploadRulesService,
                                       WorkspaceNodeRulesService workspaceNodeRulesService) {
        this.fileContentStorage = fileContentStorage;
        this.contentAssetApi = contentAssetApi;
        this.contentRegistrationApi = contentRegistrationApi;
        this.contentBlobRegistrationApi = contentBlobRegistrationApi;
        this.uploadCompletionApi = uploadCompletionApi;
        this.contentBlobLifecycleApi = contentBlobLifecycleApi;
        this.fileUploadRulesService = fileUploadRulesService;
        this.workspaceNodeRulesService = workspaceNodeRulesService;
    }

    public CreatedFile upload(WorkspaceUserContext user,
                              String path,
                              MultipartFile multipartFile,
                              ContentTypeResolver contentTypeResolver) {
        String normalizedPath = normalizeDirectoryPath(path);
        String filename = normalizeUploadFilename(multipartFile.getOriginalFilename());
        String contentType = contentTypeResolver.resolve(filename, multipartFile.getContentType());
        fileUploadRulesService.validateUpload(user, normalizedPath, filename, multipartFile.getSize());
        ensureDirectoryHierarchy(user, normalizedPath);

        String objectKey = createBlobObjectKey();
        RegisteredContentFile savedFile = contentBlobLifecycleApi.executeAfterBlobStored(objectKey, () -> {
            fileContentStorage.uploadBlob(objectKey, multipartFile);
            ContentBlobReference blob = contentBlobRegistrationApi.registerStoredBlob(objectKey, contentType, multipartFile.getSize());
            return registerBlob(user, normalizedPath, filename, contentType, multipartFile.getSize(), blob);
        });
        return new CreatedFile(normalizedPath, savedFile);
    }

    public InitiateUploadResponse initiateUpload(WorkspaceUserContext user,
                                                 InitiateUploadRequest request,
                                                 ContentTypeResolver contentTypeResolver) {
        String normalizedPath = normalizeDirectoryPath(request.path());
        String filename = normalizeLeafName(request.filename());
        String contentType = contentTypeResolver.resolve(filename, request.contentType());
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
                contentType,
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

    public CreatedFile completeUpload(WorkspaceUserContext user,
                                      CompleteUploadRequest request,
                                      ContentTypeResolver contentTypeResolver) {
        String normalizedPath = normalizeDirectoryPath(request.path());
        String filename = normalizeLeafName(request.filename());
        String objectKey = normalizeBlobObjectKey(request.storageName());
        String contentType = contentTypeResolver.resolve(filename, request.contentType());
        fileUploadRulesService.validateUpload(user, normalizedPath, filename, request.size());
        RegisteredContentFile savedFile = uploadCompletionApi.completeStoredBlob(new UploadCompletionCommand(
                user.userId(),
                normalizedPath,
                filename,
                objectKey,
                contentType,
                request.size()
        ));
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
        String normalizedPath = normalizeDirectoryPath(path);
        String normalizedFilename = normalizeLeafName(filename);
        fileUploadRulesService.validateUpload(recipient, normalizedPath, normalizedFilename, size);
        ensureDirectoryHierarchy(recipient, normalizedPath);
        String objectKey = createBlobObjectKey();
        if (writtenBlobObjectKeys != null) {
            writtenBlobObjectKeys.add(objectKey);
        }
        RegisteredContentFile savedFile = contentBlobLifecycleApi.executeAfterBlobStored(objectKey, () -> {
            fileContentStorage.storeBlob(objectKey, contentType, contentStream, size);
            ContentBlobReference blob = contentBlobRegistrationApi.registerStoredBlob(objectKey, contentType, size);
            return registerBlob(recipient, normalizedPath, normalizedFilename, contentType, size, blob);
        });
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

    public ReplacementContent replaceFileContent(WorkspaceUserContext user,
                                                 Long fileId,
                                                 String contentType,
                                                 long size,
                                                 long previousSize,
                                                 java.io.InputStream contentStream) {
        fileUploadRulesService.validateReplacement(user, previousSize, size);
        String objectKey = createBlobObjectKey();
        try {
            return contentBlobLifecycleApi.executeAfterBlobStored(objectKey, () -> {
                fileContentStorage.storeBlob(objectKey, contentType, contentStream, size);
                ContentBlobReference blob = contentBlobRegistrationApi.registerStoredBlob(objectKey, contentType, size);
                ContentPrimaryEntity primaryEntity = contentAssetApi.createOrReferencePrimaryEntity(user.userId(), blob);
                contentAssetApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(fileId, primaryEntity.entityId()));
                return new ReplacementContent(blob.blobId(), blob.objectKey(), primaryEntity.entityId());
            });
        } finally {
            try {
                contentStream.close();
            } catch (IOException ignored) {
            }
        }
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

    @FunctionalInterface
    public interface ContentTypeResolver {
        String resolve(String filename, String reportedContentType);
    }

    public record CreatedFile(String normalizedPath, RegisteredContentFile file) {
    }

    public record ReplacementContent(Long blobId, String objectKey, Long primaryEntityId) {
    }
}
