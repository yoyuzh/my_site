package com.yoyuzh.files.upload;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.platform.storage.api.UploadModePolicy;
import com.yoyuzh.platform.storage.internal.application.RuntimeUploadConstraintPolicy;
import com.yoyuzh.platform.storage.internal.application.RuntimeUploadModePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UploadPolicyResolver {

    private final UploadModePolicy uploadModePolicy;
    private final UploadConstraintPolicy uploadConstraintPolicy;

    public UploadPolicyResolver() {
        this.uploadModePolicy = new RuntimeUploadModePolicy();
        this.uploadConstraintPolicy = new RuntimeUploadConstraintPolicy();
    }

    public UploadSessionUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities) {
        return uploadModePolicy.resolveUploadMode(capabilities);
    }

    public long resolveEffectiveMaxUploadSize(long systemMaxFileSize,
                                              User user,
                                              StoragePolicy policy,
                                              StoragePolicyCapabilities capabilities) {
        return uploadConstraintPolicy.resolveEffectiveMaxUploadSize(systemMaxFileSize, user, policy, capabilities);
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
