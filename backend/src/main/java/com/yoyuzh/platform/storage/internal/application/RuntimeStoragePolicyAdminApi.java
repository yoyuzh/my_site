package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.platform.storage.internal.domain.StoragePolicy;
import com.yoyuzh.platform.storage.internal.infra.StoragePolicyRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminApi;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminUpsertCommand;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminView;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class RuntimeStoragePolicyAdminApi implements StoragePolicyAdminApi {

    private final StoragePolicyRepository storagePolicyRepository;
    private final StoragePolicyService storagePolicyService;
    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;

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
            throw new BusinessException(ErrorCode.UNKNOWN, "姒涙顓荤€涙ê鍋嶇粵鏍殣娑撳秷鍏橀崑婊呮暏");
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
        if (sourcePolicy.getId().equals(targetPolicy.getId())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "濠ф劕鐡ㄩ崒銊х摜閻ｃ儱鎷伴惄顔界垼鐎涙ê鍋嶇粵鏍殣娑撳秷鍏橀惄绋挎倱");
        }
        if (!targetPolicy.isEnabled()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "target storage policy must be enabled");
        }
        long candidateEntityCount = fileEntityRepository.countByStoragePolicyIdAndEntityType(
                sourcePolicy.getId(),
                FileEntityType.VERSION
        );
        long candidateStoredFileCount = storedFileEntityRepository.countDistinctStoredFilesByStoragePolicyIdAndEntityType(
                sourcePolicy.getId(),
                FileEntityType.VERSION
        );
        return new StoragePolicyMigrationCandidate(
                sourcePolicy.getId(),
                sourcePolicy.getName(),
                targetPolicy.getId(),
                targetPolicy.getName(),
                candidateEntityCount,
                candidateStoredFileCount,
                FileEntityType.VERSION.name()
        );
    }

    private StoragePolicy getRequiredStoragePolicy(Long policyId) {
        return storagePolicyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN, "storage policy not found"));
    }

    private void applyStoragePolicyUpsert(StoragePolicy policy, StoragePolicyAdminUpsertCommand command) {
        if (policy.isDefaultPolicy() && !command.enabled()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "姒涙顓荤€涙ê鍋嶇粵鏍殣娑撳秷鍏橀崑婊呮暏");
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
        policy.setCapabilitiesJson(storagePolicyService.writeCapabilities(command.capabilities()));
        policy.setEnabled(command.enabled());
    }

    private void validateStoragePolicyCommand(StoragePolicyAdminUpsertCommand command) {
        if (command.type() == StoragePolicyType.LOCAL
                && command.credentialMode() != StoragePolicyCredentialMode.NONE) {
            throw new BusinessException(ErrorCode.UNKNOWN, "閺堫剙婀寸€涙ê鍋嶇粵鏍殣韫囧懘銆忔担璺ㄦ暏 NONE 閸戭叀鐦夊Ο鈥崇础");
        }
        if (command.type() == StoragePolicyType.S3_COMPATIBLE
                && !StringUtils.hasText(command.bucketName())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "S3 鐎涙ê鍋嶇粵鏍殣韫囧懘銆忛幓鎰返 bucketName");
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
