package com.yoyuzh.transfer.api;

public interface TransferRuntimeMetricsPort {

    void recordTransferUsage(long bytes);

    void recordDownloadTraffic(long bytes);

    long offlineTransferStorageLimitBytes();
}
