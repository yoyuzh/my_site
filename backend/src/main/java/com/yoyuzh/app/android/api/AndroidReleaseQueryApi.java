package com.yoyuzh.app.android.api;

public interface AndroidReleaseQueryApi {

    AndroidReleaseResponse getLatestRelease();

    AndroidReleaseDownload downloadLatestRelease();
}
