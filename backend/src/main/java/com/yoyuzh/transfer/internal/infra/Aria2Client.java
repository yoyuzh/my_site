package com.yoyuzh.transfer.internal.infra;

public interface Aria2Client {

    String submitHttp(String sourceValue, String downloadNodeId);

    TaskStatus queryStatus(String gid);

    void cancel(String gid);

    record TaskStatus(
            String gid,
            String status,
            long totalBytes,
            long completedBytes,
            String outputPath,
            String errorCode,
            String errorMessage
    ) {
    }
}
