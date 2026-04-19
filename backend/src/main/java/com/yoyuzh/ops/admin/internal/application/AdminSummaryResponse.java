package com.yoyuzh.ops.admin.internal.application;

import java.util.List;

public record AdminSummaryResponse(
        long totalUsers,
        long totalFiles,
        long totalStorageBytes,
        long downloadTrafficBytes,
        long requestCount,
        long transferUsageBytes,
        long offlineTransferStorageBytes,
        long offlineTransferStorageLimitBytes,
        List<AdminDailyActiveUserSummary> dailyActiveUsers,
        List<AdminRequestTimelinePoint> requestTimeline,
        String inviteCode
) {
}
