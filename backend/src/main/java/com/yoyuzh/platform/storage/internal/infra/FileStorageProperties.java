package com.yoyuzh.platform.storage.internal.infra;

import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    private final Oss oss = new Oss();
    private final WebDav webDav = new WebDav();
    private long maxFileSize = 500L * 1024 * 1024L;
    private String pendingBlobTempDir = System.getProperty("java.io.tmpdir") + "/yoyuzh-pending-blobs";

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

    public Oss getOss() {
        return oss;
    }

    public WebDav getWebDav() {
        return webDav;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    @Override
    public String getPendingBlobTempDir() {
        return pendingBlobTempDir;
    }

    public void setPendingBlobTempDir(String pendingBlobTempDir) {
        this.pendingBlobTempDir = pendingBlobTempDir;
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

    public boolean hasOssCredentials() {
        return oss.hasCredentials();
    }

    public boolean hasWebDavCredentials() {
        return webDav.hasCredentials();
    }

    public void copyS3ApiCredentialsTo(FileStorageProperties target) {
        if (target == null) {
            throw new IllegalArgumentException("target properties must not be null");
        }
        target.getS3().replaceApiCredentials(s3.copyApiAccessKey(), s3.copyApiSecretKey());
    }

    public void copyOssCredentialsTo(FileStorageProperties target) {
        if (target == null) {
            throw new IllegalArgumentException("target properties must not be null");
        }
        target.getOss().setAccessKeyId(oss.getAccessKeyId());
        target.getOss().setAccessKeySecret(oss.getAccessKeySecret());
    }

    public void copyWebDavCredentialsTo(FileStorageProperties target) {
        if (target == null) {
            throw new IllegalArgumentException("target properties must not be null");
        }
        target.getWebDav().setUsername(webDav.getUsername());
        target.getWebDav().setPassword(webDav.getPassword());
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

    public static class Oss implements StorageRuntimeProperties.Oss {
        private String endpoint;
        private String bucketName;
        private String prefix = "";
        private String region = "cn-hangzhou";
        private String publicDownloadBaseUrl;
        private int ttlSeconds = 3600;
        private String accessKeyId;
        private String accessKeySecret;

        @Override
        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        @Override
        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        @Override
        public String getPublicDownloadBaseUrl() {
            return publicDownloadBaseUrl;
        }

        public void setPublicDownloadBaseUrl(String publicDownloadBaseUrl) {
            this.publicDownloadBaseUrl = publicDownloadBaseUrl;
        }

        @Override
        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        @Override
        public boolean hasCredentials() {
            return StringUtils.hasText(accessKeyId) && StringUtils.hasText(accessKeySecret);
        }

        @Override
        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        @Override
        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }
    }

    public static class WebDav implements StorageRuntimeProperties.WebDav {
        private String baseUrl;
        private String rootPath = "";
        private String username;
        private String password;

        @Override
        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        @Override
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        @Override
        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        @Override
        public boolean hasCredentials() {
            return StringUtils.hasText(username) && StringUtils.hasText(password);
        }
    }
}
