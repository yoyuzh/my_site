package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.identity.access.api.IdentityStorageUsageQuery;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StorageRuntimeLimitApi;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.transfer.api.TransferImportApi;
import com.yoyuzh.transfer.api.TransferImportCommand;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class RuntimeTransferImportApi implements TransferImportApi {

    private final OfflineTransferService offlineTransferService;
    private final WorkspacePathPolicy workspacePathPolicy;
    private final ContentRegistrationApi contentRegistrationApi;
    private final ContentBlobRegistrationApi contentBlobRegistrationApi;
    private final FileContentStorage fileContentStorage;
    private final StoragePolicyQuery storagePolicyQuery;
    private final UploadConstraintPolicy uploadConstraintPolicy;
    private final StorageRuntimeLimitApi storageRuntimeLimitApi;
    private final IdentityUserDirectoryApi identityUserDirectoryApi;
    private final IdentityStorageUsageQuery identityStorageUsageQuery;

    public RuntimeTransferImportApi(OfflineTransferService offlineTransferService,
                                    WorkspacePathPolicy workspacePathPolicy,
                                    ContentRegistrationApi contentRegistrationApi,
                                    ContentBlobRegistrationApi contentBlobRegistrationApi,
                                    FileContentStorage fileContentStorage,
                                    StoragePolicyQuery storagePolicyQuery,
                                    UploadConstraintPolicy uploadConstraintPolicy,
                                    StorageRuntimeLimitApi storageRuntimeLimitApi,
                                    IdentityUserDirectoryApi identityUserDirectoryApi,
                                    IdentityStorageUsageQuery identityStorageUsageQuery) {
        this.offlineTransferService = offlineTransferService;
        this.workspacePathPolicy = workspacePathPolicy;
        this.contentRegistrationApi = contentRegistrationApi;
        this.contentBlobRegistrationApi = contentBlobRegistrationApi;
        this.fileContentStorage = fileContentStorage;
        this.storagePolicyQuery = storagePolicyQuery;
        this.uploadConstraintPolicy = uploadConstraintPolicy;
        this.storageRuntimeLimitApi = storageRuntimeLimitApi;
        this.identityUserDirectoryApi = identityUserDirectoryApi;
        this.identityStorageUsageQuery = identityStorageUsageQuery;
    }

    @Override
    public FileMetadataResponse importOfflineFile(Long recipientUserId, String sessionId, String fileId, TransferImportCommand command) {
        IdentityUserSnapshot recipient = identityUserDirectoryApi.findSnapshotById(recipientUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        OfflineTransferService.ReadyOfflineTransferFile readyFile = offlineTransferService.readReadyFile(sessionId, fileId);
        String normalizedPath = workspacePathPolicy.normalizeDirectoryPath(command.path());
        String normalizedFilename = workspacePathPolicy.normalizeLeafName(readyFile.filename());

        validateImportTarget(recipient, normalizedPath, normalizedFilename, readyFile.size());
        workspacePathPolicy.ensureDirectoryHierarchy(recipient.id(), normalizedPath);

        String objectKey = createBlobObjectKey();
        try {
            try (InputStream content = readyFile.content().getInputStream()) {
                fileContentStorage.storeBlob(objectKey, readyFile.contentType(), content, readyFile.size());
            }
            var blob = contentBlobRegistrationApi.registerStoredBlob(objectKey, readyFile.contentType(), readyFile.size());
            RegisteredContentFile storedFile = contentRegistrationApi.registerBlob(new ContentRegistrationCommand(
                    recipient.id(),
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
                    storedFile.createdAt(),
                    storedFile.createdAt(),
                    false
            );
        } catch (IOException ex) {
            cleanupBlobQuietly(objectKey);
            throw new BusinessException(ErrorCode.UNKNOWN, "offline transfer file read failed");
        } catch (RuntimeException ex) {
            try {
                cleanupBlobQuietly(objectKey);
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }
    }

    private void cleanupBlobQuietly(String objectKey) {
        fileContentStorage.deleteBlob(objectKey);
    }

    private void validateImportTarget(IdentityUserSnapshot recipient, String normalizedPath, String normalizedFilename, long size) {
        DefaultStoragePolicySnapshot policySnapshot = storagePolicyQuery.readDefaultPolicySnapshot();
        StoragePolicyCapabilities capabilities = policySnapshot.capabilities();
        long effectiveMaxUploadSize = uploadConstraintPolicy.resolveEffectiveMaxUploadSize(
                storageRuntimeLimitApi.maxFileSizeBytes(),
                recipient.maxUploadSizeBytes(),
                policySnapshot.policyMaxSizeBytes(),
                capabilities == null ? 0L : capabilities.maxObjectSize()
        );
        if (size > effectiveMaxUploadSize) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "文件大小超出限制");
        }
        workspacePathPolicy.ensureNodeNameAvailable(recipient.id(), normalizedPath, normalizedFilename, "同目录下文件已存在");
        ensureWithinStorageQuota(recipient, size);
    }

    private void ensureWithinStorageQuota(IdentityUserSnapshot recipient, long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }
        long usedBytes = identityStorageUsageQuery.usedStorageBytes(recipient.id());
        long quotaBytes = recipient.storageQuotaBytes();
        if (usedBytes > Long.MAX_VALUE - additionalBytes || usedBytes + additionalBytes > quotaBytes) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "存储空间不足");
        }
    }

    private String createBlobObjectKey() {
        return "blobs/" + UUID.randomUUID();
    }
}
