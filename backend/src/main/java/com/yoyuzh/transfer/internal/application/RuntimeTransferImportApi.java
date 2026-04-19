package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileMetadataResponse;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.transfer.OfflineTransferService;
import com.yoyuzh.transfer.api.TransferImportApi;
import com.yoyuzh.transfer.api.TransferImportCommand;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RuntimeTransferImportApi implements TransferImportApi {

    private final OfflineTransferService offlineTransferService;
    private final WorkspacePathPolicy workspacePathPolicy;
    private final ContentRegistrationApi contentRegistrationApi;
    private final FileBlobRepository fileBlobRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;
    private final StoragePolicyQuery storagePolicyQuery;
    private final UploadConstraintPolicy uploadConstraintPolicy;
    private final long maxFileSize;

    public RuntimeTransferImportApi(OfflineTransferService offlineTransferService,
                                    WorkspacePathPolicy workspacePathPolicy,
                                    ContentRegistrationApi contentRegistrationApi,
                                    FileBlobRepository fileBlobRepository,
                                    StoredFileRepository storedFileRepository,
                                    FileContentStorage fileContentStorage,
                                    StoragePolicyQuery storagePolicyQuery,
                                    UploadConstraintPolicy uploadConstraintPolicy,
                                    FileStorageProperties properties) {
        this.offlineTransferService = offlineTransferService;
        this.workspacePathPolicy = workspacePathPolicy;
        this.contentRegistrationApi = contentRegistrationApi;
        this.fileBlobRepository = fileBlobRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
        this.storagePolicyQuery = storagePolicyQuery;
        this.uploadConstraintPolicy = uploadConstraintPolicy;
        this.maxFileSize = properties.getMaxFileSize();
    }

    @Override
    public FileMetadataResponse importOfflineFile(User recipient, String sessionId, String fileId, TransferImportCommand command) {
        OfflineTransferService.ReadyOfflineTransferFile readyFile = offlineTransferService.readReadyFile(sessionId, fileId);
        String normalizedPath = workspacePathPolicy.normalizeDirectoryPath(command.path());
        String normalizedFilename = workspacePathPolicy.normalizeLeafName(readyFile.filename());

        validateImportTarget(recipient, normalizedPath, normalizedFilename, readyFile.size());
        workspacePathPolicy.ensureDirectoryHierarchy(recipient, normalizedPath);

        String objectKey = createBlobObjectKey();
        try {
            fileContentStorage.storeBlob(objectKey, readyFile.contentType(), readyFile.content());
            FileBlob blob = createAndSaveBlob(objectKey, readyFile.contentType(), readyFile.size());
            RegisteredContentFile storedFile = contentRegistrationApi.registerBlob(new ContentRegistrationCommand(
                    recipient,
                    normalizedPath,
                    normalizedFilename,
                    readyFile.contentType(),
                    readyFile.size(),
                    blob
            ));
            return new FileMetadataResponse(
                    storedFile.id(),
                    storedFile.filename(),
                    storedFile.path(),
                    storedFile.size(),
                    storedFile.contentType(),
                    storedFile.directory(),
                    storedFile.createdAt()
            );
        } catch (RuntimeException ex) {
            try {
                fileContentStorage.deleteBlob(objectKey);
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }
    }

    private void validateImportTarget(User recipient, String normalizedPath, String normalizedFilename, long size) {
        DefaultStoragePolicySnapshot policySnapshot = storagePolicyQuery.readDefaultPolicySnapshot();
        StoragePolicyCapabilities capabilities = policySnapshot.capabilities();
        long effectiveMaxUploadSize = uploadConstraintPolicy.resolveEffectiveMaxUploadSize(
                maxFileSize,
                recipient,
                policySnapshot.policy(),
                capabilities
        );
        if (size > effectiveMaxUploadSize) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件大小超出限制");
        }
        workspacePathPolicy.ensureNodeNameAvailable(recipient.getId(), normalizedPath, normalizedFilename, "同目录下文件已存在");
        ensureWithinStorageQuota(recipient, size);
    }

    private void ensureWithinStorageQuota(User recipient, long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }
        long usedBytes = storedFileRepository.sumFileSizeByUserId(recipient.getId());
        long quotaBytes = recipient.getStorageQuotaBytes();
        if (usedBytes > Long.MAX_VALUE - additionalBytes || usedBytes + additionalBytes > quotaBytes) {
            throw new BusinessException(ErrorCode.UNKNOWN, "存储空间不足");
        }
    }

    private FileBlob createAndSaveBlob(String objectKey, String contentType, long size) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(size);
        return fileBlobRepository.save(blob);
    }

    private String createBlobObjectKey() {
        return "blobs/" + UUID.randomUUID();
    }
}
