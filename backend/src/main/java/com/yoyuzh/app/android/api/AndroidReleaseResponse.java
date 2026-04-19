package com.yoyuzh.app.android.api;

public record AndroidReleaseResponse(
        String downloadUrl,
        String fileName,
        String versionCode,
        String versionName,
        String publishedAt
) {
}
