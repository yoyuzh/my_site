package com.yoyuzh.transfer;

import com.yoyuzh.ops.admin.internal.application.AdminMetricsService;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OfflineTransferQuotaService {

    private final OfflineTransferSessionRepository offlineTransferSessionRepository;
    private final AdminMetricsService adminMetricsService;

    public void ensureUploadAllowed(OfflineTransferFile targetFile, long uploadSize, long maxFileSize) {
        if (uploadSize <= 0) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline file cannot be empty");
        }
        if (uploadSize > maxFileSize) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline file size exceeds limit");
        }
        if (uploadSize != targetFile.getSize()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline file size does not match session manifest");
        }

        long currentOfflineStorageBytes = offlineTransferSessionRepository.sumUploadedFileSizeByExpiresAtAfter(Instant.now());
        long additionalBytes = targetFile.isUploaded() ? 0L : targetFile.getSize();
        if (currentOfflineStorageBytes + additionalBytes > adminMetricsService.getOfflineTransferStorageLimitBytes()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline transfer storage limit exceeded");
        }
    }
}
