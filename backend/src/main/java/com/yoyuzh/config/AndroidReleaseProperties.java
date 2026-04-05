package com.yoyuzh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.android")
public class AndroidReleaseProperties {

    private String metadataObjectKey = "android/releases/latest.json";
    private String downloadPublicUrl = "https://api.yoyuzh.xyz/api/app/android/download";

    public String getMetadataObjectKey() {
        return metadataObjectKey;
    }

    public void setMetadataObjectKey(String metadataObjectKey) {
        this.metadataObjectKey = metadataObjectKey;
    }

    public String getDownloadPublicUrl() {
        return downloadPublicUrl;
    }

    public void setDownloadPublicUrl(String downloadPublicUrl) {
        this.downloadPublicUrl = downloadPublicUrl;
    }
}
