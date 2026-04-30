package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationApi;
import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationInspection;
import com.yoyuzh.platform.storage.internal.domain.StoragePolicy;
import com.yoyuzh.platform.storage.internal.infra.StoragePolicyRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminApi;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminUpsertCommand;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminView;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyBlobAccessApi;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyMigrationCandidate;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RuntimeStoragePolicyAdminApi implements StoragePolicyAdminApi {

    private final StoragePolicyRepository storagePolicyRepository;
    private final StoragePolicyService storagePolicyService;
    private final ContentStoragePolicyMigrationApi contentStoragePolicyMigrationApi;
    private final StoragePolicyBlobAccessApi storagePolicyBlobAccessApi;

    @Override
    @Transactional(readOnly = true)
    public StoragePolicyAdminView readDefaultStoragePolicyAsAdmin() {
        StoragePolicy policy = storagePolicyService.ensureDefaultPolicy();
        return toView(policy, storagePolicyService.readCapabilities(policy));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoragePolicyAdminView> listStoragePoliciesAsAdmin() {
        return storagePolicyRepository.findAll(Sort.by(Sort.Direction.DESC, "defaultPolicy")
                        .and(Sort.by(Sort.Direction.DESC, "enabled"))
                        .and(Sort.by(Sort.Direction.ASC, "id")))
                .stream()
                .map(policy -> toView(policy, storagePolicyService.readCapabilities(policy)))
                .toList();
    }

    @Override
    @Transactional
    public StoragePolicyAdminView createStoragePolicyAsAdmin(StoragePolicyAdminUpsertCommand command) {
        StoragePolicy policy = new StoragePolicy();
        policy.setDefaultPolicy(false);
        applyStoragePolicyUpsert(policy, command);
        StoragePolicy savedPolicy = storagePolicyRepository.save(policy);
        return toView(savedPolicy, storagePolicyService.readCapabilities(savedPolicy));
    }

    @Override
    @Transactional
    public StoragePolicyAdminView updateStoragePolicyAsAdmin(Long policyId, StoragePolicyAdminUpsertCommand command) {
        StoragePolicy policy = getRequiredStoragePolicy(policyId);
        applyStoragePolicyUpsert(policy, command);
        StoragePolicy savedPolicy = storagePolicyRepository.save(policy);
        return toView(savedPolicy, storagePolicyService.readCapabilities(savedPolicy));
    }

    @Override
    @Transactional
    public StoragePolicyAdminView updateStoragePolicyStatusAsAdmin(Long policyId, boolean enabled) {
        StoragePolicy policy = getRequiredStoragePolicy(policyId);
        if (policy.isDefaultPolicy() && !enabled) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "默认存储策略不能被禁用");
        }
        policy.setEnabled(enabled);
        StoragePolicy savedPolicy = storagePolicyRepository.save(policy);
        return toView(savedPolicy, storagePolicyService.readCapabilities(savedPolicy));
    }

    @Override
    @Transactional(readOnly = true)
    public StoragePolicyMigrationCandidate buildStoragePolicyMigrationCandidate(Long sourcePolicyId, Long targetPolicyId) {
        StoragePolicy sourcePolicy = getRequiredStoragePolicy(sourcePolicyId);
        StoragePolicy targetPolicy = getRequiredStoragePolicy(targetPolicyId);
        storagePolicyBlobAccessApi.validateMigration(
                storagePolicyService.readPolicyDescriptor(sourcePolicy.getId()),
                storagePolicyService.readPolicyDescriptor(targetPolicy.getId())
        );
        ContentStoragePolicyMigrationInspection inspection =
                contentStoragePolicyMigrationApi.inspectVersionItemsByStoragePolicyId(sourcePolicy.getId());
        return new StoragePolicyMigrationCandidate(
                sourcePolicy.getId(),
                sourcePolicy.getName(),
                targetPolicy.getId(),
                targetPolicy.getName(),
                inspection.entityCount(),
                inspection.storedFileCount(),
                inspection.entityType()
        );
    }

    private StoragePolicy getRequiredStoragePolicy(Long policyId) {
        return storagePolicyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORAGE_POLICY_NOT_FOUND, "storage policy not found"));
    }

    private void applyStoragePolicyUpsert(StoragePolicy policy, StoragePolicyAdminUpsertCommand command) {
        if (policy.isDefaultPolicy() && !command.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "默认存储策略不能被禁用");
        }
        validateStoragePolicyCommand(command);
        policy.setName(command.name().trim());
        policy.setType(command.type());
        policy.setBucketName(normalizeNullable(command.bucketName()));
        policy.setEndpoint(normalizeNullable(command.endpoint()));
        policy.setRegion(normalizeNullable(command.region()));
        policy.setPrivateBucket(command.privateBucket());
        policy.setPrefix(normalizePrefix(command.prefix()));
        policy.setCredentialMode(command.credentialMode());
        policy.setMaxSizeBytes(command.maxSizeBytes());
        policy.setCapabilitiesJson(storagePolicyService.writeCapabilities(
                Objects.requireNonNull(command.capabilities(), "storage policy capabilities must not be null")
        ));
        policy.setEnabled(command.enabled());
    }

    private void validateStoragePolicyCommand(StoragePolicyAdminUpsertCommand command) {
        if (command.type() == StoragePolicyType.LOCAL) {
            if (command.credentialMode() != StoragePolicyCredentialMode.NONE) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "本地存储策略的凭证模式必须为 NONE");
            }
            validateLocalStorageRoot(command.prefix());
        }
        if ((command.type() == StoragePolicyType.S3_COMPATIBLE || command.type() == StoragePolicyType.OSS_SDK)
                && !StringUtils.hasText(command.bucketName())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "对象存储策略必须配置 bucketName");
        }
        if (command.type() == StoragePolicyType.WEBDAV && !StringUtils.hasText(command.endpoint())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "WebDAV 存储策略必须配置 endpoint");
        }
    }

    private void validateLocalStorageRoot(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "本地存储策略必须配置绝对路径根目录");
        }
        try {
            if (!Path.of(prefix.trim()).isAbsolute()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "本地存储策略必须配置绝对路径根目录");
            }
        } catch (InvalidPathException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "本地存储策略根目录不合法");
        }
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        return prefix.trim();
    }

    private StoragePolicyAdminView toView(StoragePolicy policy, StoragePolicyCapabilities capabilities) {
        return new StoragePolicyAdminView(
                policy.getId(),
                policy.getName(),
                policy.getType(),
                policy.getBucketName(),
                policy.getEndpoint(),
                policy.getRegion(),
                policy.isPrivateBucket(),
                policy.getPrefix(),
                policy.getCredentialMode(),
                policy.getMaxSizeBytes(),
                capabilities,
                policy.isEnabled(),
                policy.isDefaultPolicy(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }

}
