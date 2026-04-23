package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.transfer.api.TransferRuntimeMetricsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class RuntimeTransferRuntimeMetricsPort implements TransferRuntimeMetricsPort {

    private final AdminMetricsService adminMetricsService;

    @Override
    public void recordTransferUsage(long bytes) {
        adminMetricsService.recordTransferUsage(bytes);
    }

    @Override
    public void recordDownloadTraffic(long bytes) {
        adminMetricsService.recordDownloadTraffic(bytes);
    }

    @Override
    public long offlineTransferStorageLimitBytes() {
        return adminMetricsService.getOfflineTransferStorageLimitBytes();
    }
}
