package com.yoyuzh.ops.admin.internal.application;

import java.util.List;

public record AdminMetricsSnapshot(
        long requestCount,
        long downloadTrafficBytes,
        long transferUsageBytes,
        long offlineTransferStorageLimitBytes,
        long favoriteFileCount,
        long shareDownloadCount,
        long activeTaskCount,
        List<AdminDailyActiveUserSummary> dailyActiveUsers,
        List<AdminRequestTimelinePoint> requestTimeline
) {
}
