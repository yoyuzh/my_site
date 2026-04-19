package com.yoyuzh.platform.job.api;

public record AdminBackgroundTaskQuery(
        int page,
        int size,
        String userQuery,
        BackgroundTaskType type,
        BackgroundTaskStatus status,
        BackgroundTaskFailureCategory failureCategory,
        BackgroundTaskLeaseState leaseState
) {
}
