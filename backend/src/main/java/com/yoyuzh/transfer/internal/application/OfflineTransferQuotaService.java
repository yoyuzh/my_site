package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.api.TransferRuntimeMetricsPort;
import com.yoyuzh.transfer.internal.domain.OfflineTransferFile;
import com.yoyuzh.transfer.internal.infra.OfflineTransferSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OfflineTransferQuotaService {

    private final OfflineTransferSessionRepository offlineTransferSessionRepository;
    private final TransferRuntimeMetricsPort transferRuntimeMetricsPort;

    public void ensureUploadAllowed(OfflineTransferFile targetFile, long uploadSize, long maxFileSize) {
        if (uploadSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "offline file cannot be empty");
        }
        if (uploadSize > maxFileSize) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "offline file size exceeds limit");
        }
        if (uploadSize != targetFile.getSize()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "offline file size does not match session manifest");
        }

        long currentOfflineStorageBytes = offlineTransferSessionRepository.sumUploadedFileSizeByExpiresAtAfter(Instant.now());
        long additionalBytes = targetFile.isUploaded() ? 0L : targetFile.getSize();
        if (currentOfflineStorageBytes + additionalBytes > transferRuntimeMetricsPort.offlineTransferStorageLimitBytes()) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "offline transfer storage limit exceeded");
        }
    }
}
