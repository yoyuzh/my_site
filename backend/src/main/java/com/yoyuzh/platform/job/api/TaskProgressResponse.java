package com.yoyuzh.platform.job.api;

public record TaskProgressResponse(
        Long taskId,
        String status,
        int progressPercent,
        long processedItems,
        long totalItems,
        String message
) {
}
