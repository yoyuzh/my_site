package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.files.content.api.ContentStorageFactory;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.platform.storage.api.StoragePolicyBlobAccessApi;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RuntimeStoragePolicyBlobAccessApi implements StoragePolicyBlobAccessApi {

    private static final int DEFAULT_MAX_CACHE_ENTRIES = 32;

    private final FileStorageProperties globalProperties;
    private final Map<StorageBackendKey, FileContentStorage> storageCache = new LinkedHashMap<>(16, 0.75f, true);
    private final ContentStorageFactory storageFactory;
    private final int maxCacheEntries;

    @Autowired
    public RuntimeStoragePolicyBlobAccessApi(FileStorageProperties globalProperties,
                                             ContentStorageFactory storageFactory) {
        this(globalProperties, storageFactory, DEFAULT_MAX_CACHE_ENTRIES);
    }

    RuntimeStoragePolicyBlobAccessApi(FileStorageProperties globalProperties,
                                      ContentStorageFactory storageFactory,
                                      int maxCacheEntries) {
        this.globalProperties = globalProperties;
        this.storageFactory = storageFactory;
        this.maxCacheEntries = Math.max(1, maxCacheEntries);
    }

    @Override
    public void validateMigration(StoragePolicyDescriptor sourcePolicy, StoragePolicyDescriptor targetPolicy) {
        if (sourcePolicy.id().equals(targetPolicy.id())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "源存储策略和目标存储策略不能相同");
        }
        if (!targetPolicy.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "目标存储策略必须处于启用状态");
        }
        assertSupported(sourcePolicy, "源存储策略");
        assertSupported(targetPolicy, "目标存储策略");
    }

    @Override
    public byte[] readBlob(StoragePolicyDescriptor policy, String objectKey) {
        return storageFor(policy).readBlob(normalizeObjectKey(objectKey));
    }

    @Override
    public InputStream openBlobStream(StoragePolicyDescriptor policy, String objectKey) {
        return storageFor(policy).readBlobStream(normalizeObjectKey(objectKey));
    }

    @Override
    public void storeBlob(StoragePolicyDescriptor policy, String objectKey, String contentType, byte[] content) {
        storageFor(policy).storeBlob(normalizeObjectKey(objectKey), contentType, content);
    }

    @Override
    public void storeBlob(StoragePolicyDescriptor policy,
                          String objectKey,
                          String contentType,
                          InputStream content,
                          long size) {
        storageFor(policy).storeBlob(normalizeObjectKey(objectKey), contentType, content, size);
    }

    @Override
    public void deleteBlob(StoragePolicyDescriptor policy, String objectKey) {
        storageFor(policy).deleteBlob(normalizeObjectKey(objectKey));
    }

    private FileContentStorage storageFor(StoragePolicyDescriptor policy) {
        assertSupported(policy, "存储策略");
        StorageBackendKey key = storageBackendKey(policy);
        synchronized (storageCache) {
            FileContentStorage existing = storageCache.get(key);
            if (existing != null) {
                return existing;
            }
            FileContentStorage created = storageFactory.create(storageProperties(policy));
            storageCache.put(key, created);
            evictIfNeeded();
            return created;
        }
    }

    private void assertSupported(StoragePolicyDescriptor policy, String label) {
        if (policy.type() == StoragePolicyType.LOCAL) {
            if (!StringUtils.hasText(policy.prefix())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, label + "缺少本地根目录配置");
            }
            return;
        }
        if (policy.type() == StoragePolicyType.S3_COMPATIBLE) {
            if (!StringUtils.hasText(policy.bucketName())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, label + "缺少 bucketName 配置");
            }
            if (policy.credentialMode() != StoragePolicyCredentialMode.DOGECLOUD_TEMP) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, label + "当前仅支持使用多吉云临时凭证的 S3 兼容策略迁移");
            }
            if (!globalProperties.hasS3ApiCredentials()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "当前运行环境缺少多吉云临时凭证配置，无法执行策略迁移");
            }
            return;
        }
        if (policy.type() == StoragePolicyType.OSS_SDK) {
            if (!StringUtils.hasText(policy.bucketName())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, label + "缺少 bucketName 配置");
            }
            if (!globalProperties.hasOssCredentials()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "当前运行环境缺少 OSS 凭证配置，无法执行策略迁移");
            }
            return;
        }
        if (policy.type() == StoragePolicyType.WEBDAV) {
            if (!StringUtils.hasText(policy.endpoint())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, label + "缺少 WebDAV endpoint 配置");
            }
            if (!globalProperties.hasWebDavCredentials()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "当前运行环境缺少 WebDAV 凭证配置，无法执行策略迁移");
            }
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, label + "类型不支持迁移");
    }

    private FileStorageProperties localProperties(StoragePolicyDescriptor policy) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider("local");
        properties.setMaxFileSize(globalProperties.getMaxFileSize());
        properties.getLocal().setRootDir(policy.prefix().trim());
        return properties;
    }

    private FileStorageProperties s3Properties(StoragePolicyDescriptor policy) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider("s3");
        properties.setMaxFileSize(globalProperties.getMaxFileSize());
        properties.getS3().setApiBaseUrl(globalProperties.getS3().getApiBaseUrl());
        globalProperties.copyS3ApiCredentialsTo(properties);
        properties.getS3().setTtlSeconds(globalProperties.getS3().getTtlSeconds());
        properties.getS3().setRegion(StringUtils.hasText(policy.region()) ? policy.region().trim() : globalProperties.getS3().getRegion());
        properties.getS3().setScope(buildScope(policy.bucketName(), policy.prefix()));
        return properties;
    }

    private FileStorageProperties storageProperties(StoragePolicyDescriptor policy) {
        return switch (policy.type()) {
            case LOCAL -> localProperties(policy);
            case S3_COMPATIBLE -> s3Properties(policy);
            case OSS_SDK -> ossProperties(policy);
            case WEBDAV -> webDavProperties(policy);
        };
    }

    private FileStorageProperties ossProperties(StoragePolicyDescriptor policy) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider("oss");
        properties.setMaxFileSize(globalProperties.getMaxFileSize());
        properties.getOss().setEndpoint(StringUtils.hasText(policy.endpoint()) ? policy.endpoint().trim() : globalProperties.getOss().getEndpoint());
        properties.getOss().setBucketName(policy.bucketName());
        properties.getOss().setPrefix(policy.prefix());
        properties.getOss().setRegion(StringUtils.hasText(policy.region()) ? policy.region().trim() : globalProperties.getOss().getRegion());
        properties.getOss().setPublicDownloadBaseUrl(globalProperties.getOss().getPublicDownloadBaseUrl());
        properties.getOss().setTtlSeconds(globalProperties.getOss().getTtlSeconds());
        globalProperties.copyOssCredentialsTo(properties);
        return properties;
    }

    private FileStorageProperties webDavProperties(StoragePolicyDescriptor policy) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider("webdav");
        properties.setMaxFileSize(globalProperties.getMaxFileSize());
        properties.getWebDav().setBaseUrl(StringUtils.hasText(policy.endpoint()) ? policy.endpoint().trim() : globalProperties.getWebDav().getBaseUrl());
        properties.getWebDav().setRootPath(policy.prefix());
        globalProperties.copyWebDavCredentialsTo(properties);
        return properties;
    }

    private StorageBackendKey storageBackendKey(StoragePolicyDescriptor policy) {
        return new StorageBackendKey(
                policy.type(),
                normalizeCacheValue(policy.bucketName()),
                normalizeCacheValue(policy.endpoint()),
                normalizeCacheValue(policy.region()),
                normalizeCacheValue(policy.prefix()),
                policy.credentialMode()
        );
    }

    private String normalizeCacheValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeObjectKey(String objectKey) {
        String cleaned = StringUtils.cleanPath(objectKey == null ? "" : objectKey).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.startsWith("/") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid storage object key");
        }
        return cleaned;
    }

    @PreDestroy
    void closeCachedStorages() {
        synchronized (storageCache) {
            storageCache.values().forEach(this::closeStorage);
            storageCache.clear();
        }
    }

    private void evictIfNeeded() {
        while (storageCache.size() > maxCacheEntries) {
            Iterator<Map.Entry<StorageBackendKey, FileContentStorage>> iterator = storageCache.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            Map.Entry<StorageBackendKey, FileContentStorage> eldest = iterator.next();
            iterator.remove();
            closeStorage(eldest.getValue());
        }
    }

    private void closeStorage(FileContentStorage storage) {
        if (storage instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (Exception ignored) {
                // Best-effort cleanup for evicted storage adapters.
            }
        }
    }

    private String buildScope(String bucketName, String prefix) {
        String trimmedBucket = bucketName.trim();
        if (!StringUtils.hasText(prefix)) {
            return trimmedBucket;
        }
        String normalizedPrefix = prefix.trim();
        return trimmedBucket + ":" + normalizedPrefix;
    }

    private record StorageBackendKey(StoragePolicyType type,
                                     String bucketName,
                                     String endpoint,
                                     String region,
                                     String prefix,
                                     StoragePolicyCredentialMode credentialMode) {
    }
}
