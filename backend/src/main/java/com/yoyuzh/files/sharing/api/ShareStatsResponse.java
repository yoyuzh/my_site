package com.yoyuzh.files.sharing.api;

public record ShareStatsResponse(
        String token,
        long visits,
        long downloads,
        Integer maxDownloads,
        boolean downloadLimitReached
) {
}
