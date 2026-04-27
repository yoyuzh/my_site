package com.yoyuzh.platform.storage.internal.infra;

import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties implements StorageRuntimeProperties {

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

    public boolean hasS3ApiCredentials() {
        return s3.hasApiCredentials();
    }

    public void copyS3ApiCredentialsTo(FileStorageProperties target) {
        if (target == null) {
            throw new IllegalArgumentException("target properties must not be null");
        }
        target.getS3().setApiAccessKey(s3.apiAccessKey);
        target.getS3().setApiSecretKey(s3.apiSecretKey);
    }

    public static class Local implements StorageRuntimeProperties.Local {
        private String rootDir = "./storage";

        public String getRootDir() {
            return rootDir;
        }

        public void setRootDir(String rootDir) {
            this.rootDir = rootDir;
        }
    }

    public static class S3 implements StorageRuntimeProperties.S3 {
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

        public void setApiAccessKey(String apiAccessKey) {
            this.apiAccessKey = apiAccessKey;
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

        @Override
        public boolean hasApiCredentials() {
            return apiAccessKey != null
                    && !apiAccessKey.isBlank()
                    && apiSecretKey != null
                    && !apiSecretKey.isBlank();
        }

        @Override
        public String createApiAuthorization(String signTarget) {
            if (!hasApiCredentials()) {
                throw new IllegalStateException("S3 API credentials are not configured");
            }
            return "TOKEN " + apiAccessKey + ":" + hmacSha1Hex(apiSecretKey, signTarget);
        }

        private String hmacSha1Hex(String secret, String content) {
            try {
                Mac mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
                byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
                StringBuilder builder = new StringBuilder(digest.length * 2);
                for (byte current : digest) {
                    builder.append(String.format("%02x", current));
                }
                return builder.toString();
            } catch (Exception ex) {
                throw new IllegalStateException("生成多吉云 API 签名失败", ex);
            }
        }
    }
}
