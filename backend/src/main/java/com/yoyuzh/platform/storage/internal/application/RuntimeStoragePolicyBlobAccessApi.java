package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.LocalFileContentStorage;
import com.yoyuzh.files.storage.S3FileContentStorage;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
public class RuntimeStoragePolicyBlobAccessApi implements StoragePolicyBlobAccessApi {

    private final FileStorageProperties globalProperties;
    private final Map<StorageBackendKey, FileContentStorage> storageCache = new ConcurrentHashMap<>();
    private final Function<StoragePolicyDescriptor, FileContentStorage> storageFactory;

    @Autowired
    public RuntimeStoragePolicyBlobAccessApi(FileStorageProperties globalProperties) {
        this(globalProperties, null);
    }

    RuntimeStoragePolicyBlobAccessApi(FileStorageProperties globalProperties,
                                      Function<StoragePolicyDescriptor, FileContentStorage> storageFactory) {
        this.globalProperties = globalProperties;
        this.storageFactory = storageFactory != null ? storageFactory : this::createStorage;
    }

    @Override
    public void validateMigration(StoragePolicyDescriptor sourcePolicy, StoragePolicyDescriptor targetPolicy) {
        if (sourcePolicy.id().equals(targetPolicy.id())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "源存储策略和目标存储策略不能相同");
        }
        if (!targetPolicy.enabled()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目标存储策略必须处于启用状态");
        }
        assertSupported(sourcePolicy, "源存储策略");
        assertSupported(targetPolicy, "目标存储策略");
    }

    @Override
    public byte[] readBlob(StoragePolicyDescriptor policy, String objectKey) {
        return storageFor(policy).readBlob(objectKey);
    }

    @Override
    public void storeBlob(StoragePolicyDescriptor policy, String objectKey, String contentType, byte[] content) {
        storageFor(policy).storeBlob(objectKey, contentType, content);
    }

    @Override
    public void deleteBlob(StoragePolicyDescriptor policy, String objectKey) {
        storageFor(policy).deleteBlob(objectKey);
    }

    private FileContentStorage storageFor(StoragePolicyDescriptor policy) {
        assertSupported(policy, "存储策略");
        return storageCache.computeIfAbsent(storageBackendKey(policy), ignored -> storageFactory.apply(policy));
    }

    private void assertSupported(StoragePolicyDescriptor policy, String label) {
        if (policy.type() == StoragePolicyType.LOCAL) {
            if (!StringUtils.hasText(policy.prefix())) {
                throw new BusinessException(ErrorCode.UNKNOWN, label + "缺少本地根目录配置");
            }
            return;
        }
        if (policy.type() != StoragePolicyType.S3_COMPATIBLE) {
            throw new BusinessException(ErrorCode.UNKNOWN, label + "类型不支持迁移");
        }
        if (!StringUtils.hasText(policy.bucketName())) {
            throw new BusinessException(ErrorCode.UNKNOWN, label + "缺少 bucketName 配置");
        }
        if (policy.credentialMode() != StoragePolicyCredentialMode.DOGECLOUD_TEMP) {
            throw new BusinessException(ErrorCode.UNKNOWN, label + "当前仅支持使用多吉云临时凭证的 S3 兼容策略迁移");
        }
        if (!StringUtils.hasText(globalProperties.getS3().getApiAccessKey())
                || !StringUtils.hasText(globalProperties.getS3().getApiSecretKey())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "当前运行环境缺少多吉云临时凭证配置，无法执行策略迁移");
        }
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
        properties.getS3().setApiAccessKey(globalProperties.getS3().getApiAccessKey());
        properties.getS3().setApiSecretKey(globalProperties.getS3().getApiSecretKey());
        properties.getS3().setTtlSeconds(globalProperties.getS3().getTtlSeconds());
        properties.getS3().setRegion(StringUtils.hasText(policy.region()) ? policy.region().trim() : globalProperties.getS3().getRegion());
        properties.getS3().setScope(buildScope(policy.bucketName(), policy.prefix()));
        return properties;
    }

    private FileContentStorage createStorage(StoragePolicyDescriptor policy) {
        return switch (policy.type()) {
            case LOCAL -> new LocalFileContentStorage(localProperties(policy));
            case S3_COMPATIBLE -> new S3FileContentStorage(s3Properties(policy));
        };
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

    @PreDestroy
    void closeCachedStorages() {
        storageCache.values().forEach(storage -> {
            if (storage instanceof S3FileContentStorage s3Storage) {
                s3Storage.close();
            }
        });
        storageCache.clear();
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
