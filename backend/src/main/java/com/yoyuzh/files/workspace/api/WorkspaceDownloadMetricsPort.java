package com.yoyuzh.files.workspace.api;

public interface WorkspaceDownloadMetricsPort {

    void recordDownloadTraffic(long bytes);

    static WorkspaceDownloadMetricsPort noOp() {
        return bytes -> {
        };
    }
}
