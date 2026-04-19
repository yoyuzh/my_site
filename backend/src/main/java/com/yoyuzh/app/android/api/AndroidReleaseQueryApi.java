package com.yoyuzh.app.android.api;

import com.yoyuzh.config.AndroidReleaseDownload;
import com.yoyuzh.config.AndroidReleaseResponse;

public interface AndroidReleaseQueryApi {

    AndroidReleaseResponse getLatestRelease();

    AndroidReleaseDownload downloadLatestRelease();
}
