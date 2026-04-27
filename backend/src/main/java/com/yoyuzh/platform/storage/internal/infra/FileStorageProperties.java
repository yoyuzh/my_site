package com.yoyuzh.platform.storage.internal.infra;

import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
        target.getS3().replaceApiCredentials(s3.copyApiAccessKey(), s3.copyApiSecretKey());
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
        // Keep credentials in char[] so callers can explicitly zero them after use,
        // which reduces their lifetime in memory and lowers heap-dump exposure risk.
        private char[] apiAccessKey = new char[0];
        private char[] apiSecretKey = new char[0];
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
            this.apiAccessKey = replaceSecret(this.apiAccessKey, apiAccessKey);
        }

        public void setApiSecretKey(String apiSecretKey) {
            this.apiSecretKey = replaceSecret(this.apiSecretKey, apiSecretKey);
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
            return hasText(apiAccessKey) && hasText(apiSecretKey);
        }

        @Override
        public String createApiAuthorization(String signTarget) {
            if (!hasApiCredentials()) {
                throw new IllegalStateException("S3 API credentials are not configured");
            }
            return "TOKEN " + new String(apiAccessKey) + ":" + hmacSha1Hex(apiSecretKey, signTarget);
        }

        private void replaceApiCredentials(char[] accessKey, char[] secretKey) {
            apiAccessKey = replaceSecret(apiAccessKey, accessKey);
            apiSecretKey = replaceSecret(apiSecretKey, secretKey);
        }

        private char[] copyApiAccessKey() {
            return Arrays.copyOf(apiAccessKey, apiAccessKey.length);
        }

        private char[] copyApiSecretKey() {
            return Arrays.copyOf(apiSecretKey, apiSecretKey.length);
        }

        private String hmacSha1Hex(char[] secret, String content) {
            byte[] secretBytes = toUtf8(secret);
            try {
                Mac mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(secretBytes, "HmacSHA1"));
                byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
                StringBuilder builder = new StringBuilder(digest.length * 2);
                for (byte current : digest) {
                    builder.append(String.format("%02x", current));
                }
                return builder.toString();
            } catch (Exception ex) {
                throw new IllegalStateException("生成多吉云 API 签名失败", ex);
            } finally {
                Arrays.fill(secretBytes, (byte) 0);
            }
        }

        private static char[] replaceSecret(char[] current, String updated) {
            return replaceSecret(current, updated == null ? new char[0] : updated.toCharArray());
        }

        private static char[] replaceSecret(char[] current, char[] updated) {
            if (current != null) {
                Arrays.fill(current, '\0');
            }
            return updated == null ? new char[0] : Arrays.copyOf(updated, updated.length);
        }

        private static boolean hasText(char[] value) {
            if (value == null || value.length == 0) {
                return false;
            }
            for (char current : value) {
                if (!Character.isWhitespace(current)) {
                    return true;
                }
            }
            return false;
        }

        private static byte[] toUtf8(char[] value) {
            ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
            byte[] bytes = Arrays.copyOfRange(encoded.array(), encoded.position(), encoded.limit());
            Arrays.fill(encoded.array(), (byte) 0);
            return bytes;
        }
    }
}
