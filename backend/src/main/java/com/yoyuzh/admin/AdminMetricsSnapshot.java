package com.yoyuzh.admin;

import java.util.List;

public record AdminMetricsSnapshot(
        long requestCount,
        long downloadTrafficBytes,
        long transferUsageBytes,
        long offlineTransferStorageLimitBytes,
        List<AdminRequestTimelinePoint> requestTimeline
) {
}
