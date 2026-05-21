package com.yoyuzh.platform.storage.api;

public interface StorageRuntimeProperties {

    String getProvider();

    Local getLocal();

    S3 getS3();

    Oss getOss();

    WebDav getWebDav();

    long getMaxFileSize();

    String getPendingBlobTempDir();

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

    interface Oss {
        String getEndpoint();

        String getBucketName();

        String getPrefix();

        String getRegion();

        String getPublicDownloadBaseUrl();

        int getTtlSeconds();

        boolean hasCredentials();

        String getAccessKeyId();

        String getAccessKeySecret();
    }

    interface WebDav {
        String getBaseUrl();

        String getRootPath();

        String getUsername();

        String getPassword();

        boolean hasCredentials();
    }
}
