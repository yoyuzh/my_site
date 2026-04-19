package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.upload.api.UploadTargetPolicy;
import com.yoyuzh.files.upload.api.ValidatedUploadTarget;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeUploadTargetPolicy implements UploadTargetPolicy {

    private final StoredFileRepository storedFileRepository;
    private final WorkspacePathPolicy workspacePathPolicy;
    private final StoragePolicyQuery storagePolicyQuery;
    private final UploadConstraintPolicy uploadConstraintPolicy;
    private final long maxFileSize;

    @Autowired
    public RuntimeUploadTargetPolicy(StoredFileRepository storedFileRepository,
                                     WorkspacePathPolicy workspacePathPolicy,
                                     StoragePolicyQuery storagePolicyQuery,
                                     UploadConstraintPolicy uploadConstraintPolicy,
                                     com.yoyuzh.config.FileStorageProperties properties) {
        this(storedFileRepository, workspacePathPolicy, storagePolicyQuery, uploadConstraintPolicy, properties.getMaxFileSize());
    }

    RuntimeUploadTargetPolicy(StoredFileRepository storedFileRepository,
                              WorkspacePathPolicy workspacePathPolicy,
                              StoragePolicyQuery storagePolicyQuery,
                              UploadConstraintPolicy uploadConstraintPolicy,
                              long maxFileSize) {
        this.storedFileRepository = storedFileRepository;
        this.workspacePathPolicy = workspacePathPolicy;
        this.storagePolicyQuery = storagePolicyQuery;
        this.uploadConstraintPolicy = uploadConstraintPolicy;
        this.maxFileSize = maxFileSize;
    }

    @Override
    public ValidatedUploadTarget validateUpload(User user, String path, String filename, long size) {
        String normalizedPath = workspacePathPolicy.normalizeDirectoryPath(path);
        String normalizedFilename = workspacePathPolicy.normalizeLeafName(filename);
        DefaultStoragePolicySnapshot defaultPolicySnapshot = storagePolicyQuery.readDefaultPolicySnapshot();
        long effectiveMaxUploadSize = uploadConstraintPolicy.resolveEffectiveMaxUploadSize(
                maxFileSize,
                user,
                defaultPolicySnapshot.policy(),
                defaultPolicySnapshot.capabilities()
        );
        if (size > effectiveMaxUploadSize) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件大小超出限制");
        }
        workspacePathPolicy.ensureNodeNameAvailable(user.getId(), normalizedPath, normalizedFilename, "同目录下文件已存在");
        ensureWithinStorageQuota(user, size);
        return new ValidatedUploadTarget(normalizedPath, normalizedFilename, defaultPolicySnapshot);
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
}
