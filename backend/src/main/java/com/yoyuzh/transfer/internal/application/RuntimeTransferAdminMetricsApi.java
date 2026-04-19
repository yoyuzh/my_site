package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.transfer.OfflineTransferSessionRepository;
import com.yoyuzh.transfer.api.TransferAdminMetricsApi;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTransferAdminMetricsApi implements TransferAdminMetricsApi {

    private final OfflineTransferSessionRepository offlineTransferSessionRepository;

    @Override
    public long currentOfflineStorageBytes() {
        return offlineTransferSessionRepository.sumUploadedFileSizeByExpiresAtAfter(Instant.now());
    }
}
