package com.yoyuzh.admin;

public record AdminRequestTimelinePoint(
        int hour,
        String label,
        long requestCount
) {
}
