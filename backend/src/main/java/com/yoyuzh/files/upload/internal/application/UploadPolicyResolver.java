package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.files.upload.api.UploadSessionUploadMode;
import com.yoyuzh.files.upload.internal.domain.UploadSession;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StorageUploadMode;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.platform.storage.api.UploadModePolicy;
import org.springframework.stereotype.Component;

@Component
public class UploadPolicyResolver {

    private final UploadModePolicy uploadModePolicy;
    private final UploadConstraintPolicy uploadConstraintPolicy;

    public UploadPolicyResolver(UploadModePolicy uploadModePolicy,
                                UploadConstraintPolicy uploadConstraintPolicy) {
        this.uploadModePolicy = uploadModePolicy;
        this.uploadConstraintPolicy = uploadConstraintPolicy;
    }

    public UploadSessionUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities) {
        return toUploadSessionMode(uploadModePolicy.resolveUploadMode(capabilities));
    }

    public long resolveEffectiveMaxUploadSize(long systemMaxFileSize,
                                              long userMaxUploadSizeBytes,
                                              long policyMaxSizeBytes,
                                              StoragePolicyCapabilities capabilities) {
        return uploadConstraintPolicy.resolveEffectiveMaxUploadSize(
                systemMaxFileSize,
                userMaxUploadSizeBytes,
                policyMaxSizeBytes,
                capabilities == null ? 0L : capabilities.maxObjectSize()
        );
    }

    public int calculateChunkCount(long size, long chunkSize) {
        if (size <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) size / chunkSize);
    }

    public long resolveChunkSize(UploadSession session, int partIndex) {
        if (partIndex < 0 || partIndex >= session.getChunkCount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid part index");
        }
        if (partIndex < session.getChunkCount() - 1) {
            return session.getChunkSize();
        }
        long remaining = session.getSize() - session.getChunkSize() * (session.getChunkCount() - 1L);
        return remaining > 0 ? remaining : session.getChunkSize();
    }

    private UploadSessionUploadMode toUploadSessionMode(StorageUploadMode mode) {
        if (mode == StorageUploadMode.PROXY) {
            return UploadSessionUploadMode.PROXY;
        }
        if (mode == StorageUploadMode.DIRECT_SINGLE) {
            return UploadSessionUploadMode.DIRECT_SINGLE;
        }
        return UploadSessionUploadMode.DIRECT_MULTIPART;
    }

    static StorageUploadMode resolveDefaultUploadMode(StoragePolicyCapabilities capabilities) {
        if (!capabilities.directUpload()) {
            return StorageUploadMode.PROXY;
        }
        if (capabilities.multipartUpload()) {
            return StorageUploadMode.DIRECT_MULTIPART;
        }
        return StorageUploadMode.DIRECT_SINGLE;
    }

    static long resolveDefaultEffectiveMaxUploadSize(long systemMaxFileSize,
                                                     long userMaxUploadSizeBytes,
                                                     long policyMaxSizeBytes,
                                                     long maxObjectSize) {
        long effectiveMaxUploadSize = Math.min(systemMaxFileSize, userMaxUploadSizeBytes);
        if (policyMaxSizeBytes > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, policyMaxSizeBytes);
        }
        if (maxObjectSize > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, maxObjectSize);
        }
        return effectiveMaxUploadSize;
    }
}
