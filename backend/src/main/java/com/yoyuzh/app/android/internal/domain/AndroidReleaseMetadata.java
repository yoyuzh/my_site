package com.yoyuzh.app.android.internal.domain;

public record AndroidReleaseMetadata(
        String objectKey,
        String fileName,
        String versionCode,
        String versionName,
        String publishedAt
) {
}
