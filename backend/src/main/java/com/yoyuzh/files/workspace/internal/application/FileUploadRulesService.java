package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;

public final class FileUploadRulesService {

    private final StoredFileRepository storedFileRepository;
    private final StoragePolicyQuery storagePolicyQuery;
    private final UploadConstraintPolicy uploadConstraintPolicy;
    private final WorkspaceNodeRulesService workspaceNodeRulesService;
    private final long maxFileSize;

    public FileUploadRulesService(StoredFileRepository storedFileRepository,
                                  StoragePolicyQuery storagePolicyQuery,
                                  UploadConstraintPolicy uploadConstraintPolicy,
                                  WorkspaceNodeRulesService workspaceNodeRulesService,
                                  long maxFileSize) {
        this.storedFileRepository = storedFileRepository;
        this.storagePolicyQuery = storagePolicyQuery;
        this.uploadConstraintPolicy = uploadConstraintPolicy;
        this.workspaceNodeRulesService = workspaceNodeRulesService;
        this.maxFileSize = maxFileSize;
    }

    public void validateUpload(WorkspaceUserContext user, String normalizedPath, String filename, long size) {
        long effectiveMaxUploadSize = resolveEffectiveMaxUploadSize(user);
        if (size > effectiveMaxUploadSize) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "文件大小超出限制");
        }
        ensureWithinStorageQuota(user, size);
    }

    public void validateReplacement(WorkspaceUserContext user, long previousSize, long nextSize) {
        long effectiveMaxUploadSize = resolveEffectiveMaxUploadSize(user);
        if (nextSize > effectiveMaxUploadSize) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "文件大小超出限制");
        }
        ensureWithinStorageQuota(user, Math.max(0L, nextSize - Math.max(previousSize, 0L)));
    }

    public void ensureWithinStorageQuota(WorkspaceUserContext user, long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }

        long usedBytes = storedFileRepository.sumFileSizeByUserId(user.userId());
        long quotaBytes = user.storageQuotaBytes();
        if (usedBytes > Long.MAX_VALUE - additionalBytes || usedBytes + additionalBytes > quotaBytes) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "存储空间不足");
        }
    }

    private long resolveEffectiveMaxUploadSize(WorkspaceUserContext user) {
        long effectiveMaxUploadSize = maxFileSize;
        if (user.maxUploadSizeBytes() != null && user.maxUploadSizeBytes() > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, user.maxUploadSizeBytes());
        }
        long policyMaxSizeBytes = 0L;
        StoragePolicyCapabilities capabilities = null;
        if (storagePolicyQuery != null) {
            var defaultPolicySnapshot = storagePolicyQuery.readDefaultPolicySnapshot();
            policyMaxSizeBytes = defaultPolicySnapshot.policyMaxSizeBytes();
            capabilities = defaultPolicySnapshot.capabilities();
        }
        if (uploadConstraintPolicy != null) {
            return uploadConstraintPolicy.resolveEffectiveMaxUploadSize(
                    maxFileSize,
                    user.maxUploadSizeBytes() == null || user.maxUploadSizeBytes() <= 0 ? maxFileSize : user.maxUploadSizeBytes(),
                    policyMaxSizeBytes,
                    capabilities == null ? 0L : capabilities.maxObjectSize()
            );
        }
        if (policyMaxSizeBytes > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, policyMaxSizeBytes);
        }
        if (capabilities != null && capabilities.maxObjectSize() > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, capabilities.maxObjectSize());
        }
        return effectiveMaxUploadSize;
    }
}
