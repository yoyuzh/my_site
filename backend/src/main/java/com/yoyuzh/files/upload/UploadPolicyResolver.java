package com.yoyuzh.files.upload;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import org.springframework.stereotype.Component;

@Component
public class UploadPolicyResolver {

    public UploadSessionUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities) {
        if (!capabilities.directUpload()) {
            return UploadSessionUploadMode.PROXY;
        }
        if (capabilities.multipartUpload()) {
            return UploadSessionUploadMode.DIRECT_MULTIPART;
        }
        return UploadSessionUploadMode.DIRECT_SINGLE;
    }

    public long resolveEffectiveMaxUploadSize(long systemMaxFileSize,
                                              User user,
                                              StoragePolicy policy,
                                              StoragePolicyCapabilities capabilities) {
        long effectiveMaxUploadSize = Math.min(systemMaxFileSize, user.getMaxUploadSizeBytes());
        if (policy.getMaxSizeBytes() > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, policy.getMaxSizeBytes());
        }
        if (capabilities.maxObjectSize() > 0) {
            effectiveMaxUploadSize = Math.min(effectiveMaxUploadSize, capabilities.maxObjectSize());
        }
        return effectiveMaxUploadSize;
    }

    public int calculateChunkCount(long size, long chunkSize) {
        if (size <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) size / chunkSize);
    }

    public long resolveChunkSize(UploadSession session, int partIndex) {
        if (partIndex < 0 || partIndex >= session.getChunkCount()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "鍒嗙墖搴忓彿涓嶅悎娉?");
        }
        if (partIndex < session.getChunkCount() - 1) {
            return session.getChunkSize();
        }
        long remaining = session.getSize() - session.getChunkSize() * (session.getChunkCount() - 1L);
        return remaining > 0 ? remaining : session.getChunkSize();
    }
}
