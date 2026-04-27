package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationApi;
import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationItem;
import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationMutation;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyBlobAccessApi;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StoragePolicyMigrationBackgroundTaskHandler implements BackgroundTaskHandler {

    private final StoragePolicyQuery storagePolicyQuery;
    private final ContentStoragePolicyMigrationApi contentStoragePolicyMigrationApi;
    private final StoragePolicyBlobAccessApi storagePolicyBlobAccessApi;
    private final BackgroundTaskStateManager stateManager;

    public StoragePolicyMigrationBackgroundTaskHandler(StoragePolicyQuery storagePolicyQuery,
                                                       ContentStoragePolicyMigrationApi contentStoragePolicyMigrationApi,
                                                       StoragePolicyBlobAccessApi storagePolicyBlobAccessApi,
                                                       BackgroundTaskStateManager stateManager) {
        this.storagePolicyQuery = storagePolicyQuery;
        this.contentStoragePolicyMigrationApi = contentStoragePolicyMigrationApi;
        this.storagePolicyBlobAccessApi = storagePolicyBlobAccessApi;
        this.stateManager = stateManager;
    }

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.STORAGE_POLICY_MIGRATION;
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task) {
        return handle(task, publicStatePatch -> {
        });
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        Map<String, Object> state = stateManager.parseJsonObject(
                task.getPrivateStateJson(),
                "storage policy migration task state is invalid"
        );
        Long sourcePolicyId = readLong(state.get("sourcePolicyId"), "sourcePolicyId");
        Long targetPolicyId = readLong(state.get("targetPolicyId"), "targetPolicyId");

        StoragePolicyDescriptor sourcePolicy = storagePolicyQuery.readPolicyDescriptor(sourcePolicyId);
        StoragePolicyDescriptor targetPolicy = storagePolicyQuery.readPolicyDescriptor(targetPolicyId);
        validatePolicyPair(sourcePolicy, targetPolicy);

        List<ContentStoragePolicyMigrationItem> entities =
                contentStoragePolicyMigrationApi.listVersionItemsByStoragePolicyId(sourcePolicyId);
        long candidateEntityCount = entities.size();
        long candidateStoredFileCount = 0L;
        for (ContentStoragePolicyMigrationItem entity : entities) {
            validateTargetCapacity(entity, targetPolicy);
            candidateStoredFileCount += entity.linkedStoredFileCount();
        }

        long processedEntityCount = 0L;
        long migratedStoredFileCount = 0L;
        List<String> copiedObjectKeys = new ArrayList<>();
        LinkedHashSet<String> staleObjectKeys = new LinkedHashSet<>();
        List<ContentStoragePolicyMigrationMutation> mutations = new ArrayList<>();
        progressReporter.report(progressPatch(
                sourcePolicy,
                targetPolicy,
                candidateEntityCount,
                candidateStoredFileCount,
                0L,
                0L,
                0L,
                "copying-object-data",
                false
        ));
        try {
            for (ContentStoragePolicyMigrationItem entity : entities) {
                long storedFileCount = entity.linkedStoredFileCount();
                String oldObjectKey = entity.objectKey();
                String newObjectKey = buildTargetObjectKey(targetPolicy.id());
                String contentType = StringUtils.hasText(entity.contentType()) ? entity.contentType() : entity.blobContentType();

                try (InputStream content = storagePolicyBlobAccessApi.openBlobStream(sourcePolicy, oldObjectKey)) {
                    copiedObjectKeys.add(newObjectKey);
                    storagePolicyBlobAccessApi.storeBlob(
                            targetPolicy,
                            newObjectKey,
                            contentType,
                            content,
                            entity.blobSize() == null ? 0L : entity.blobSize()
                    );
                } catch (IOException ex) {
                    throw new IllegalStateException("storage policy migration failed to close blob stream", ex);
                }
                mutations.add(new ContentStoragePolicyMigrationMutation(entity.entityId(), entity.blobId(), newObjectKey));

                staleObjectKeys.add(oldObjectKey);
                processedEntityCount += 1;
                migratedStoredFileCount += storedFileCount;
                progressReporter.report(progressPatch(
                        sourcePolicy,
                        targetPolicy,
                        candidateEntityCount,
                        candidateStoredFileCount,
                        processedEntityCount,
                        processedEntityCount,
                        migratedStoredFileCount,
                        "copying-object-data",
                        false
                ));
            }
        } catch (RuntimeException ex) {
            cleanupCopiedObjects(targetPolicy, copiedObjectKeys);
            throw ex;
        }

        try {
            contentStoragePolicyMigrationApi.reassignVersionItems(targetPolicy.id(), mutations);
        } catch (RuntimeException ex) {
            cleanupCopiedObjects(targetPolicy, copiedObjectKeys);
            throw ex;
        }
        cleanupStaleObjects(sourcePolicy, staleObjectKeys);
        return new BackgroundTaskHandlerResult(progressPatch(
                sourcePolicy,
                targetPolicy,
                candidateEntityCount,
                candidateStoredFileCount,
                processedEntityCount,
                processedEntityCount,
                migratedStoredFileCount,
                "completed",
                true
        ));
    }

    private void validatePolicyPair(StoragePolicyDescriptor sourcePolicy, StoragePolicyDescriptor targetPolicy) {
        storagePolicyBlobAccessApi.validateMigration(sourcePolicy, targetPolicy);
    }

    private void validateTargetCapacity(ContentStoragePolicyMigrationItem entity, StoragePolicyDescriptor targetPolicy) {
        if (targetPolicy.maxSizeBytes() > 0 && entity.size() != null && entity.size() > targetPolicy.maxSizeBytes()) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "目标存储策略容量上限不足以承载待迁移对象");
        }
    }

    private String buildTargetObjectKey(Long targetPolicyId) {
        return "policies/" + targetPolicyId + "/blobs/" + UUID.randomUUID().toString().replace("-", "");
    }

    private Map<String, Object> progressPatch(StoragePolicyDescriptor sourcePolicy,
                                              StoragePolicyDescriptor targetPolicy,
                                              long candidateEntityCount,
                                              long candidateStoredFileCount,
                                              long processedEntityCount,
                                              long migratedEntityCount,
                                              long migratedStoredFileCount,
                                              String migrationStage,
                                              boolean migrationPerformed) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(BackgroundTaskStateKeys.PHASE, "migrating-storage-policy");
        patch.put("worker", "storage-policy-migration");
        patch.put("migrationStage", migrationStage);
        patch.put("migrationMode", migrationPerformed ? "executed" : "executing");
        patch.put("migrationPerformed", migrationPerformed);
        patch.put("sourcePolicyId", sourcePolicy.id());
        patch.put("sourcePolicyName", sourcePolicy.name());
        patch.put("targetPolicyId", targetPolicy.id());
        patch.put("targetPolicyName", targetPolicy.name());
        patch.put("candidateEntityCount", candidateEntityCount);
        patch.put("candidateStoredFileCount", candidateStoredFileCount);
        patch.put("processedEntityCount", processedEntityCount);
        patch.put("totalEntityCount", candidateEntityCount);
        patch.put("processedStoredFileCount", migratedStoredFileCount);
        patch.put("totalStoredFileCount", candidateStoredFileCount);
        patch.put("migratedEntityCount", migratedEntityCount);
        patch.put("migratedStoredFileCount", migratedStoredFileCount);
        patch.put("entityType", ContentStoragePolicyMigrationApi.VERSION_ENTITY_TYPE);
        patch.put("plannedAt", LocalDateTime.now().toString());
        patch.put("progressPercent", calculateProgressPercent(
                processedEntityCount,
                candidateEntityCount,
                migratedStoredFileCount,
                candidateStoredFileCount
        ));
        patch.put("message", migrationPerformed
                ? "storage policy migration moved object data through the active storage backend and updated metadata references"
                : "storage policy migration is copying object data and updating metadata references");
        return patch;
    }

    private int calculateProgressPercent(long processedEntityCount,
                                         long totalEntityCount,
                                         long processedStoredFileCount,
                                         long totalStoredFileCount) {
        long total = Math.max(0L, totalEntityCount) + Math.max(0L, totalStoredFileCount);
        long processed = Math.max(0L, processedEntityCount) + Math.max(0L, processedStoredFileCount);
        if (total <= 0L) {
            return 100;
        }
        return (int) Math.min(100L, Math.floor((processed * 100.0d) / total));
    }

    private void cleanupStaleObjects(StoragePolicyDescriptor sourcePolicy, LinkedHashSet<String> staleObjectKeys) {
        if (staleObjectKeys.isEmpty()) {
            return;
        }
        for (String staleObjectKey : staleObjectKeys) {
            try {
                storagePolicyBlobAccessApi.deleteBlob(sourcePolicy, staleObjectKey);
            } catch (RuntimeException ignored) {
                // Metadata update already committed; leave source cleanup as best effort.
            }
        }
    }

    private void cleanupCopiedObjects(StoragePolicyDescriptor targetPolicy, List<String> copiedObjectKeys) {
        for (String copiedObjectKey : copiedObjectKeys) {
            try {
                storagePolicyBlobAccessApi.deleteBlob(targetPolicy, copiedObjectKey);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup while metadata rolls back.
            }
        }
    }

    private Long readLong(Object value, String key) {
        Long parsed = stateManager.readLong(value);
        if (parsed != null) {
            return parsed;
        }
        throw new IllegalStateException("storage policy migration task missing " + key);
    }
}
