package com.yoyuzh.files.core;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyService;

public final class FileUploadRulesService {

    private final StoredFileRepository storedFileRepository;
    private final StoragePolicyService storagePolicyService;
    private final WorkspaceNodeRulesService workspaceNodeRulesService;
    private final long maxFileSize;

    public FileUploadRulesService(StoredFileRepository storedFileRepository,
                                  StoragePolicyService storagePolicyService,
                                  WorkspaceNodeRulesService workspaceNodeRulesService,
                                  long maxFileSize) {
        this.storedFileRepository = storedFileRepository;
        this.storagePolicyService = storagePolicyService;
        this.workspaceNodeRulesService = workspaceNodeRulesService;
        this.maxFileSize = maxFileSize;
    }

    public void validateUpload(User user, String normalizedPath, String filename, long size) {
        long effectiveMaxUploadSize = Math.min(maxFileSize, user.getMaxUploadSizeBytes());
        StoragePolicy defaultPolicy = storagePolicyService == null ? null : storagePolicyService.ensureDefaultPolicy();
        StoragePolicyCapabilities capabilities = defaultPolicy == null ? null : storagePolicyService.readCapabilities(defaultPolicy);
        if (defaultPolicy != null && defaultPolicy.getMaxSizeBytes() > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, defaultPolicy.getMaxSizeBytes());
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

    public void ensureWithinStorageQuota(User user, long additionalBytes) {
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
