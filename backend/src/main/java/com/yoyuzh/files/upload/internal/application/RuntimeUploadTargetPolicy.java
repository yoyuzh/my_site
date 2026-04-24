package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.upload.api.UploadTargetPolicy;
import com.yoyuzh.files.upload.api.ValidatedUploadTarget;
import com.yoyuzh.files.workspace.api.WorkspaceFileQueryApi;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeUploadTargetPolicy implements UploadTargetPolicy {

    private final WorkspaceFileQueryApi workspaceFileQueryApi;
    private final WorkspacePathPolicy workspacePathPolicy;
    private final StoragePolicyQuery storagePolicyQuery;
    private final UploadConstraintPolicy uploadConstraintPolicy;
    private final long maxFileSize;

    @Autowired
    public RuntimeUploadTargetPolicy(WorkspaceFileQueryApi workspaceFileQueryApi,
                                     WorkspacePathPolicy workspacePathPolicy,
                                     StoragePolicyQuery storagePolicyQuery,
                                     UploadConstraintPolicy uploadConstraintPolicy,
                                     StorageRuntimeProperties storageRuntimeProperties) {
        this(workspaceFileQueryApi, workspacePathPolicy, storagePolicyQuery, uploadConstraintPolicy, storageRuntimeProperties.getMaxFileSize());
    }

    RuntimeUploadTargetPolicy(WorkspaceFileQueryApi workspaceFileQueryApi,
                              WorkspacePathPolicy workspacePathPolicy,
                              StoragePolicyQuery storagePolicyQuery,
                              UploadConstraintPolicy uploadConstraintPolicy,
                              long maxFileSize) {
        this.workspaceFileQueryApi = workspaceFileQueryApi;
        this.workspacePathPolicy = workspacePathPolicy;
        this.storagePolicyQuery = storagePolicyQuery;
        this.uploadConstraintPolicy = uploadConstraintPolicy;
        this.maxFileSize = maxFileSize;
    }

    @Override
    public ValidatedUploadTarget validateUpload(Long userId,
                                                Long maxUploadSizeBytes,
                                                long storageQuotaBytes,
                                                String path,
                                                String filename,
                                                long size) {
        String normalizedPath = workspacePathPolicy.normalizeDirectoryPath(path);
        String normalizedFilename = workspacePathPolicy.normalizeLeafName(filename);
        DefaultStoragePolicySnapshot defaultPolicySnapshot = storagePolicyQuery.readDefaultPolicySnapshot();
        long effectiveMaxUploadSize = uploadConstraintPolicy.resolveEffectiveMaxUploadSize(
                maxFileSize,
                maxUploadSizeBytes,
                defaultPolicySnapshot.policyMaxSizeBytes(),
                defaultPolicySnapshot.capabilities() == null ? 0L : defaultPolicySnapshot.capabilities().maxObjectSize()
        );
        if (size > effectiveMaxUploadSize) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "文件大小超出限制");
        }
        workspacePathPolicy.ensureNodeNameAvailable(userId, normalizedPath, normalizedFilename, "同目录下文件已存在");
        ensureWithinStorageQuota(userId, storageQuotaBytes, size);
        return new ValidatedUploadTarget(normalizedPath, normalizedFilename, defaultPolicySnapshot);
    }

    private void ensureWithinStorageQuota(Long userId, long storageQuotaBytes, long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }

        long usedBytes = workspaceFileQueryApi.sumFileSizeByUserId(userId);
        if (usedBytes > Long.MAX_VALUE - additionalBytes || usedBytes + additionalBytes > storageQuotaBytes) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "存储空间不足");
        }
    }
}
