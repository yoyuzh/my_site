package com.yoyuzh.config;

public record AndroidReleaseMetadata(
        String objectKey,
        String fileName,
        String versionCode,
        String versionName,
        String publishedAt
) {
}
