package com.yoyuzh.transfer.api;

public record RemoteDownloadExecutionResult(
        Long remoteDownloadId,
        String engineType,
        String phase,
        String downloaderTaskId,
        boolean completed,
        boolean failed,
        String failureMessage,
        Long nextRunDelaySeconds
) {
}
