package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyBlobAccessApi;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Transactional
public class StoragePolicyMigrationBackgroundTaskHandler implements BackgroundTaskHandler {

    private final StoragePolicyQuery storagePolicyQuery;
    private final FileEntityRepository fileEntityRepository;
    private final FileBlobRepository fileBlobRepository;
    private final StoredFileRepository storedFileRepository;
    private final StoragePolicyBlobAccessApi storagePolicyBlobAccessApi;
    private final BackgroundTaskStateManager stateManager;

    public StoragePolicyMigrationBackgroundTaskHandler(StoragePolicyQuery storagePolicyQuery,
                                                       FileEntityRepository fileEntityRepository,
                                                       FileBlobRepository fileBlobRepository,
                                                       StoredFileRepository storedFileRepository,
                                                       StoragePolicyBlobAccessApi storagePolicyBlobAccessApi,
                                                       BackgroundTaskStateManager stateManager) {
        this.storagePolicyQuery = storagePolicyQuery;
        this.fileEntityRepository = fileEntityRepository;
        this.fileBlobRepository = fileBlobRepository;
        this.storedFileRepository = storedFileRepository;
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

        List<FileEntity> entities = fileEntityRepository.findByStoragePolicyIdAndEntityTypeOrderByIdAsc(
                sourcePolicyId,
                FileEntityType.VERSION
        );
        long candidateEntityCount = entities.size();
        long candidateStoredFileCount = 0L;
        for (FileEntity entity : entities) {
            validateTargetCapacity(entity, targetPolicy);
            candidateStoredFileCount += storedFileRepository.countByBlobId(getRequiredBlob(entity).getId());
        }

        long processedEntityCount = 0L;
        long migratedStoredFileCount = 0L;
        List<String> copiedObjectKeys = new ArrayList<>();
        LinkedHashSet<String> staleObjectKeys = new LinkedHashSet<>();
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
            for (FileEntity entity : entities) {
                FileBlob blob = getRequiredBlob(entity);
                long storedFileCount = storedFileRepository.countByBlobId(blob.getId());
                String oldObjectKey = entity.getObjectKey();
                String newObjectKey = buildTargetObjectKey(targetPolicy.id());
                String contentType = StringUtils.hasText(entity.getContentType()) ? entity.getContentType() : blob.getContentType();

                byte[] content = storagePolicyBlobAccessApi.readBlob(sourcePolicy, oldObjectKey);
                copiedObjectKeys.add(newObjectKey);
                storagePolicyBlobAccessApi.storeBlob(targetPolicy, newObjectKey, contentType, content);

                entity.setObjectKey(newObjectKey);
                entity.setStoragePolicyId(targetPolicy.id());
                fileEntityRepository.save(entity);

                blob.setObjectKey(newObjectKey);
                fileBlobRepository.save(blob);

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

        scheduleStaleObjectCleanup(sourcePolicy, staleObjectKeys);
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

    private void validateTargetCapacity(FileEntity entity, StoragePolicyDescriptor targetPolicy) {
        if (targetPolicy.maxSizeBytes() > 0 && entity.getSize() != null && entity.getSize() > targetPolicy.maxSizeBytes()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目标存储策略容量上限不足以承载待迁移对象");
        }
    }

    private FileBlob getRequiredBlob(FileEntity entity) {
        return fileBlobRepository.findByObjectKey(entity.getObjectKey())
                .orElseThrow(() -> new IllegalStateException("storage policy migration blob not found"));
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
        patch.put("entityType", FileEntityType.VERSION.name());
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

    private void scheduleStaleObjectCleanup(StoragePolicyDescriptor sourcePolicy, LinkedHashSet<String> staleObjectKeys) {
        if (staleObjectKeys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String staleObjectKey : staleObjectKeys) {
                    try {
                        storagePolicyBlobAccessApi.deleteBlob(sourcePolicy, staleObjectKey);
                    } catch (RuntimeException ignored) {
                        // Database state already committed; leave old object cleanup as best effort.
                    }
                }
            }
        });
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
