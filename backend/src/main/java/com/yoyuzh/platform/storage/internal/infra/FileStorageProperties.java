package com.yoyuzh.platform.storage.internal.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {

    private String provider = "local";
    private final Local local = new Local();
    private final S3 s3 = new S3();
    private long maxFileSize = 500L * 1024 * 1024L;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Local getLocal() {
        return local;
    }

    public S3 getS3() {
        return s3;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    // Backward-compatible convenience accessors used by existing tests and dev tooling.
    public String getRootDir() {
        return local.getRootDir();
    }

    public void setRootDir(String rootDir) {
        local.setRootDir(rootDir);
    }

    public static class Local {
        private String rootDir = "./storage";

        public String getRootDir() {
            return rootDir;
        }

        public void setRootDir(String rootDir) {
            this.rootDir = rootDir;
        }
    }

    public static class S3 {
        private String apiBaseUrl = "https://api.dogecloud.com";
        private String apiAccessKey;
        private String apiSecretKey;
        private String scope;
        private int ttlSeconds = 3600;
        private String region = "automatic";
        private String publicDownloadBaseUrl;
        private String packageDownloadBaseUrl;
        private String packageDownloadSecret;
        private int packageDownloadTtlSeconds = 300;

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getApiAccessKey() {
            return apiAccessKey;
        }

        public void setApiAccessKey(String apiAccessKey) {
            this.apiAccessKey = apiAccessKey;
        }

        public String getApiSecretKey() {
            return apiSecretKey;
        }

        public void setApiSecretKey(String apiSecretKey) {
            this.apiSecretKey = apiSecretKey;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getPublicDownloadBaseUrl() {
            return publicDownloadBaseUrl;
        }

        public void setPublicDownloadBaseUrl(String publicDownloadBaseUrl) {
            this.publicDownloadBaseUrl = publicDownloadBaseUrl;
        }

        public String getPackageDownloadBaseUrl() {
            return packageDownloadBaseUrl;
        }

        public void setPackageDownloadBaseUrl(String packageDownloadBaseUrl) {
            this.packageDownloadBaseUrl = packageDownloadBaseUrl;
        }

        public String getPackageDownloadSecret() {
            return packageDownloadSecret;
        }

        public void setPackageDownloadSecret(String packageDownloadSecret) {
            this.packageDownloadSecret = packageDownloadSecret;
        }

        public int getPackageDownloadTtlSeconds() {
            return packageDownloadTtlSeconds;
        }

        public void setPackageDownloadTtlSeconds(int packageDownloadTtlSeconds) {
            this.packageDownloadTtlSeconds = packageDownloadTtlSeconds;
        }
    }
}
