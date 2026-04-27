package com.yoyuzh.platform.storage.api;

public interface StorageRuntimeProperties {

    String getProvider();

    Local getLocal();

    S3 getS3();

    long getMaxFileSize();

    interface Local {
        String getRootDir();
    }

    interface S3 {
        String getApiBaseUrl();

        String getScope();

        int getTtlSeconds();

        String getRegion();

        String getPublicDownloadBaseUrl();

        String getPackageDownloadBaseUrl();

        String getPackageDownloadSecret();

        int getPackageDownloadTtlSeconds();

        boolean hasApiCredentials();

        String createApiAuthorization(String signTarget);
    }
}
