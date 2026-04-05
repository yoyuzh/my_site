package com.yoyuzh.config;

public record AndroidReleaseResponse(
        String downloadUrl,
        String fileName,
        String versionCode,
        String versionName,
        String publishedAt
) {
}
