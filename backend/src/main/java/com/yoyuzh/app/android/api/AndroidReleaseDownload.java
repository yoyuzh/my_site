package com.yoyuzh.app.android.api;

public record AndroidReleaseDownload(
        String fileName,
        byte[] content
) {
}
