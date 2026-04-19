package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.ops.admin.internal.web.AdminStoragePolicyMigrationCreateRequest;
import com.yoyuzh.ops.admin.internal.web.AdminStoragePolicyUpsertRequest;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.infra.cache.RedisCacheNames;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStorageGovernanceService {

    private final StoragePolicyRepository storagePolicyRepository;
    private final StoragePolicyService storagePolicyService;
    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;
    private final BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    private final AdminAuditService adminAuditService;

    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.STORAGE_POLICIES, allEntries = true)
    public AdminStoragePolicyResponse createStoragePolicy(AdminStoragePolicyUpsertRequest request) {
        StoragePolicy policy = new StoragePolicy();
        policy.setDefaultPolicy(false);
        applyStoragePolicyUpsert(policy, request);
        StoragePolicy savedPolicy = storagePolicyRepository.save(policy);
        AdminStoragePolicyResponse response = AdminStoragePolicyResponses.from(
                savedPolicy,
                storagePolicyService.readCapabilities(savedPolicy)
        );
        adminAuditService.record(
                AdminAuditAction.CREATE_STORAGE_POLICY,
                "STORAGE_POLICY",
                response.id(),
                "Created storage policy",
                Map.of(
                        "name", response.name(),
                        "type", response.type().name(),
                        "enabled", response.enabled()
                )
        );
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.STORAGE_POLICIES, allEntries = true)
    public AdminStoragePolicyResponse updateStoragePolicy(Long policyId, AdminStoragePolicyUpsertRequest request) {
        StoragePolicy policy = getRequiredStoragePolicy(policyId);
        applyStoragePolicyUpsert(policy, request);
        StoragePolicy savedPolicy = storagePolicyRepository.save(policy);
        AdminStoragePolicyResponse response = AdminStoragePolicyResponses.from(
                savedPolicy,
                storagePolicyService.readCapabilities(savedPolicy)
        );
        adminAuditService.record(
                AdminAuditAction.UPDATE_STORAGE_POLICY,
                "STORAGE_POLICY",
                policyId,
                "Updated storage policy",
                Map.of(
                        "name", response.name(),
                        "type", response.type().name(),
                        "enabled", response.enabled()
                )
        );
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.STORAGE_POLICIES, allEntries = true)
    public AdminStoragePolicyResponse updateStoragePolicyStatus(Long policyId, boolean enabled) {
        StoragePolicy policy = getRequiredStoragePolicy(policyId);
        if (policy.isDefaultPolicy() && !enabled) {
            throw new BusinessException(ErrorCode.UNKNOWN, "姒涙顓荤€涙ê鍋嶇粵鏍殣娑撳秷鍏橀崑婊呮暏");
        }
        policy.setEnabled(enabled);
        StoragePolicy savedPolicy = storagePolicyRepository.save(policy);
        AdminStoragePolicyResponse response = AdminStoragePolicyResponses.from(
                savedPolicy,
                storagePolicyService.readCapabilities(savedPolicy)
        );
        adminAuditService.record(
                AdminAuditAction.UPDATE_STORAGE_POLICY_STATUS,
                "STORAGE_POLICY",
                policyId,
                enabled ? "Enabled storage policy" : "Disabled storage policy",
                Map.of("enabled", enabled)
        );
        return response;
    }

    @Transactional
    public BackgroundTaskView createStoragePolicyMigrationTask(User user, AdminStoragePolicyMigrationCreateRequest request) {
        StoragePolicy sourcePolicy = getRequiredStoragePolicy(request.sourcePolicyId());
        StoragePolicy targetPolicy = getRequiredStoragePolicy(request.targetPolicyId());
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

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("sourcePolicyId", sourcePolicy.getId());
        state.put("sourcePolicyName", sourcePolicy.getName());
        state.put("targetPolicyId", targetPolicy.getId());
        state.put("targetPolicyName", targetPolicy.getName());
        state.put("candidateEntityCount", candidateEntityCount);
        state.put("candidateStoredFileCount", candidateStoredFileCount);
        state.put("migrationPerformed", false);
        state.put("migrationMode", "skeleton");
        state.put("entityType", FileEntityType.VERSION.name());
        state.put("message", "storage policy migration skeleton queued; worker will validate and recount candidates without moving object data");

        Map<String, Object> privateState = new LinkedHashMap<>(state);
        privateState.put("taskType", BackgroundTaskType.STORAGE_POLICY_MIGRATION.name());

        BackgroundTaskView task = backgroundTaskLifecycleApi.createQueuedTask(
                user,
                BackgroundTaskType.STORAGE_POLICY_MIGRATION,
                state,
                privateState,
                request.correlationId()
        );
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("sourcePolicyId", sourcePolicy.getId());
        auditDetails.put("targetPolicyId", targetPolicy.getId());
        auditDetails.put("correlationId", request.correlationId());
        adminAuditService.record(
                AdminAuditAction.CREATE_STORAGE_POLICY_MIGRATION_TASK,
                "TASK",
                task.id(),
                "Created storage policy migration task",
                auditDetails
        );
        return task;
    }

    private void applyStoragePolicyUpsert(StoragePolicy policy, AdminStoragePolicyUpsertRequest request) {
        if (policy.isDefaultPolicy() && !request.enabled()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "姒涙顓荤€涙ê鍋嶇粵鏍殣娑撳秷鍏橀崑婊呮暏");
        }
        validateStoragePolicyRequest(request);
        policy.setName(request.name().trim());
        policy.setType(request.type());
        policy.setBucketName(normalizeNullable(request.bucketName()));
        policy.setEndpoint(normalizeNullable(request.endpoint()));
        policy.setRegion(normalizeNullable(request.region()));
        policy.setPrivateBucket(request.privateBucket());
        policy.setPrefix(normalizePrefix(request.prefix()));
        policy.setCredentialMode(request.credentialMode());
        policy.setMaxSizeBytes(request.maxSizeBytes());
        policy.setCapabilitiesJson(storagePolicyService.writeCapabilities(request.capabilities()));
        policy.setEnabled(request.enabled());
    }

    private StoragePolicy getRequiredStoragePolicy(Long policyId) {
        return storagePolicyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN, "storage policy not found"));
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

    private void validateStoragePolicyRequest(AdminStoragePolicyUpsertRequest request) {
        if (request.type() == com.yoyuzh.files.policy.StoragePolicyType.LOCAL
                && request.credentialMode() != com.yoyuzh.files.policy.StoragePolicyCredentialMode.NONE) {
            throw new BusinessException(ErrorCode.UNKNOWN, "閺堫剙婀寸€涙ê鍋嶇粵鏍殣韫囧懘銆忔担璺ㄦ暏 NONE 閸戭叀鐦夊Ο鈥崇础");
        }
        if (request.type() == com.yoyuzh.files.policy.StoragePolicyType.S3_COMPATIBLE
                && !StringUtils.hasText(request.bucketName())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "S3 鐎涙ê鍋嶇粵鏍殣韫囧懘銆忛幓鎰返 bucketName");
        }
    }
}
