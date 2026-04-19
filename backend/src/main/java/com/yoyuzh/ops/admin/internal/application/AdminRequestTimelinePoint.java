package com.yoyuzh.ops.admin.internal.application;

public record AdminRequestTimelinePoint(
        int hour,
        String label,
        long requestCount
) {
}
