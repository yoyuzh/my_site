package com.yoyuzh.app.android.internal.application;

import com.yoyuzh.app.android.api.AndroidReleaseQueryApi;
import com.yoyuzh.config.AndroidReleaseDownload;
import com.yoyuzh.config.AndroidReleaseResponse;
import com.yoyuzh.config.AndroidReleaseService;
import org.springframework.stereotype.Service;

@Service
public class RuntimeAndroidReleaseQueryApi implements AndroidReleaseQueryApi {

    private final AndroidReleaseService androidReleaseService;

    public RuntimeAndroidReleaseQueryApi(AndroidReleaseService androidReleaseService) {
        this.androidReleaseService = androidReleaseService;
    }

    @Override
    public AndroidReleaseResponse getLatestRelease() {
        return androidReleaseService.getLatestRelease();
    }

    @Override
    public AndroidReleaseDownload downloadLatestRelease() {
        return androidReleaseService.downloadLatestRelease();
    }
}
