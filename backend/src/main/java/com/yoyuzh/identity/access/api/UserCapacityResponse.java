package com.yoyuzh.identity.access.api;

public record UserCapacityResponse(
        long totalBytes,
        long usedBytes,
        long availableBytes,
        long maxUploadSizeBytes
) {
}
