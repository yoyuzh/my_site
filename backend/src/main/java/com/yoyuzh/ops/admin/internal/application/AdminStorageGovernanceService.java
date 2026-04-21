package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.infra.cache.RedisCacheNames;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminApi;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminUpsertCommand;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminView;
import com.yoyuzh.platform.storage.api.StoragePolicyMigrationCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStorageGovernanceService {

    private final StoragePolicyAdminApi storagePolicyAdminApi;
    private final BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    private final AdminAuditService adminAuditService;

    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.STORAGE_POLICIES, allEntries = true)
    public AdminStoragePolicyResponse createStoragePolicy(AdminStoragePolicyUpsertInput input) {
        StoragePolicyAdminView storagePolicy =
                storagePolicyAdminApi.createStoragePolicyAsAdmin(toUpsertCommand(input));
        AdminStoragePolicyResponse response = AdminStoragePolicyResponses.from(storagePolicy);
        adminAuditService.record(
                AdminAuditAction.CREATE_STORAGE_POLICY,
                "STORAGE_POLICY",
                response.id(),
                "Created storage policy",
                Map.of(
                        "name", response.name(),
                        "enabled", response.enabled()
                )
        );
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.STORAGE_POLICIES, allEntries = true)
    public AdminStoragePolicyResponse updateStoragePolicy(Long policyId, AdminStoragePolicyUpsertInput input) {
        StoragePolicyAdminView storagePolicy =
                storagePolicyAdminApi.updateStoragePolicyAsAdmin(policyId, toUpsertCommand(input));
        AdminStoragePolicyResponse response = AdminStoragePolicyResponses.from(storagePolicy);
        adminAuditService.record(
                AdminAuditAction.UPDATE_STORAGE_POLICY,
                "STORAGE_POLICY",
                policyId,
                "Updated storage policy",
                Map.of(
                        "name", response.name(),
                        "enabled", response.enabled()
                )
        );
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.STORAGE_POLICIES, allEntries = true)
    public AdminStoragePolicyResponse updateStoragePolicyStatus(Long policyId, boolean enabled) {
        StoragePolicyAdminView storagePolicy =
                storagePolicyAdminApi.updateStoragePolicyStatusAsAdmin(policyId, enabled);
        AdminStoragePolicyResponse response = AdminStoragePolicyResponses.from(storagePolicy);
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
    public BackgroundTaskView createStoragePolicyMigrationTask(Long userId, AdminStoragePolicyMigrationInput input) {
        StoragePolicyMigrationCandidate candidate =
                storagePolicyAdminApi.buildStoragePolicyMigrationCandidate(input.sourcePolicyId(), input.targetPolicyId());

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("sourcePolicyId", candidate.sourcePolicyId());
        state.put("sourcePolicyName", candidate.sourcePolicyName());
        state.put("targetPolicyId", candidate.targetPolicyId());
        state.put("targetPolicyName", candidate.targetPolicyName());
        state.put("candidateEntityCount", candidate.candidateEntityCount());
        state.put("candidateStoredFileCount", candidate.candidateStoredFileCount());
        state.put("migrationPerformed", false);
        state.put("migrationMode", "skeleton");
        state.put("entityType", candidate.entityType());
        state.put("message", "storage policy migration skeleton queued; worker will validate and recount candidates without moving object data");

        Map<String, Object> privateState = new LinkedHashMap<>(state);
        privateState.put("taskType", BackgroundTaskType.STORAGE_POLICY_MIGRATION.name());

        BackgroundTaskView task = backgroundTaskLifecycleApi.createQueuedTaskByUserId(
                userId,
                BackgroundTaskType.STORAGE_POLICY_MIGRATION,
                state,
                privateState,
                input.correlationId()
        );
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("sourcePolicyId", candidate.sourcePolicyId());
        auditDetails.put("targetPolicyId", candidate.targetPolicyId());
        auditDetails.put("correlationId", input.correlationId());
        adminAuditService.record(
                AdminAuditAction.CREATE_STORAGE_POLICY_MIGRATION_TASK,
                "TASK",
                task.id(),
                "Created storage policy migration task",
                auditDetails
        );
        return task;
    }

    private StoragePolicyAdminUpsertCommand toUpsertCommand(AdminStoragePolicyUpsertInput input) {
        return new StoragePolicyAdminUpsertCommand(
                input.name(),
                input.type(),
                input.bucketName(),
                input.endpoint(),
                input.region(),
                input.privateBucket(),
                input.prefix(),
                input.credentialMode(),
                input.maxSizeBytes(),
                input.capabilities(),
                input.enabled()
        );
    }
}
