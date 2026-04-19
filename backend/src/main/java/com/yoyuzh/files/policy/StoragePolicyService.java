package com.yoyuzh.files.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.upload.UploadSessionUploadMode;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.platform.storage.api.UploadModePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Order(-1)
@RequiredArgsConstructor
public class StoragePolicyService implements CommandLineRunner, StoragePolicyQuery {

    private final StoragePolicyRepository storagePolicyRepository;
    private final FileStorageProperties properties;
    private final UploadModePolicy uploadModePolicy;
    private final UploadConstraintPolicy uploadConstraintPolicy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public void run(String... args) {
        ensureDefaultPolicy();
    }

    @Transactional
    public StoragePolicy ensureDefaultPolicy() {
        return storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc()
                .orElseGet(() -> storagePolicyRepository.save(createDefaultPolicy()));
    }

    public StoragePolicyCapabilities readCapabilities(StoragePolicy policy) {
        try {
            return objectMapper.readValue(policy.getCapabilitiesJson(), StoragePolicyCapabilities.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Storage policy capabilities are invalid", ex);
        }
    }

    @Override
    public DefaultStoragePolicySnapshot readDefaultPolicySnapshot() {
        StoragePolicy policy = ensureDefaultPolicy();
        return new DefaultStoragePolicySnapshot(policy, readCapabilities(policy));
    }

    public String writeCapabilities(StoragePolicyCapabilities capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities);
        } catch (Exception ex) {
            throw new IllegalStateException("Storage policy capabilities cannot be serialized", ex);
        }
    }

    public UploadSessionUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities) {
        return uploadModePolicy.resolveUploadMode(capabilities);
    }

    public long resolveEffectiveMaxUploadSize(long systemMaxFileSize,
                                              User user,
                                              StoragePolicy policy,
                                              StoragePolicyCapabilities capabilities) {
        return uploadConstraintPolicy.resolveEffectiveMaxUploadSize(systemMaxFileSize, user, policy, capabilities);
    }

    public StoragePolicy getRequiredPolicy(Long policyId) {
        return storagePolicyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN, "存储策略不存在"));
    }

    private StoragePolicy createDefaultPolicy() {
        if ("s3".equalsIgnoreCase(properties.getProvider())) {
            return createDefaultS3Policy();
        }
        return createDefaultLocalPolicy();
    }

    private StoragePolicy createDefaultS3Policy() {
        StoragePolicy policy = new StoragePolicy();
        policy.setName("Default S3 Compatible Storage");
        policy.setType(StoragePolicyType.S3_COMPATIBLE);
        policy.setBucketName(extractScopeBucketName(properties.getS3().getScope()));
        policy.setRegion(properties.getS3().getRegion());
        policy.setPrivateBucket(true);
        policy.setPrefix(extractScopePrefix(properties.getS3().getScope()));
        policy.setCredentialMode(StoragePolicyCredentialMode.DOGECLOUD_TEMP);
        policy.setMaxSizeBytes(properties.getMaxFileSize());
        policy.setCapabilitiesJson(writeCapabilities(new StoragePolicyCapabilities(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                properties.getMaxFileSize()
        )));
        policy.setEnabled(true);
        policy.setDefaultPolicy(true);
        return policy;
    }

    private StoragePolicy createDefaultLocalPolicy() {
        StoragePolicy policy = new StoragePolicy();
        policy.setName("Default Local Storage");
        policy.setType(StoragePolicyType.LOCAL);
        policy.setPrivateBucket(true);
        policy.setPrefix(properties.getLocal().getRootDir());
        policy.setCredentialMode(StoragePolicyCredentialMode.NONE);
        policy.setMaxSizeBytes(properties.getMaxFileSize());
        policy.setCapabilitiesJson(writeCapabilities(new StoragePolicyCapabilities(
                false,
                false,
                false,
                true,
                false,
                true,
                false,
                false,
                properties.getMaxFileSize()
        )));
        policy.setEnabled(true);
        policy.setDefaultPolicy(true);
        return policy;
    }

    private String extractScopeBucketName(String scope) {
        if (!StringUtils.hasText(scope)) {
            return null;
        }
        int separatorIndex = scope.indexOf(':');
        return separatorIndex >= 0 ? scope.substring(0, separatorIndex) : scope;
    }

    private String extractScopePrefix(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "";
        }
        int separatorIndex = scope.indexOf(':');
        return separatorIndex >= 0 ? scope.substring(separatorIndex + 1) : "";
    }
}
