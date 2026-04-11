package com.yoyuzh.files.tasks;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyType;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.LocalFileContentStorage;
import com.yoyuzh.files.storage.S3FileContentStorage;
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

    private final StoragePolicyRepository storagePolicyRepository;
    private final FileEntityRepository fileEntityRepository;
    private final FileBlobRepository fileBlobRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;
    private final BackgroundTaskStateManager stateManager;

    public StoragePolicyMigrationBackgroundTaskHandler(StoragePolicyRepository storagePolicyRepository,
                                                       FileEntityRepository fileEntityRepository,
                                                       FileBlobRepository fileBlobRepository,
                                                       StoredFileRepository storedFileRepository,
                                                       FileContentStorage fileContentStorage,
                                                       BackgroundTaskStateManager stateManager) {
        this.storagePolicyRepository = storagePolicyRepository;
        this.fileEntityRepository = fileEntityRepository;
        this.fileBlobRepository = fileBlobRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
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

        StoragePolicy sourcePolicy = storagePolicyRepository.findById(sourcePolicyId)
                .orElseThrow(() -> new IllegalStateException("storage policy migration source policy not found"));
        StoragePolicy targetPolicy = storagePolicyRepository.findById(targetPolicyId)
                .orElseThrow(() -> new IllegalStateException("storage policy migration target policy not found"));
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
                String newObjectKey = buildTargetObjectKey(targetPolicy.getId());
                String contentType = StringUtils.hasText(entity.getContentType()) ? entity.getContentType() : blob.getContentType();

                byte[] content = fileContentStorage.readBlob(oldObjectKey);
                copiedObjectKeys.add(newObjectKey);
                fileContentStorage.storeBlob(newObjectKey, contentType, content);

                entity.setObjectKey(newObjectKey);
                entity.setStoragePolicyId(targetPolicy.getId());
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
            cleanupCopiedObjects(copiedObjectKeys);
            throw ex;
        }

        scheduleStaleObjectCleanup(staleObjectKeys);
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

    private void validatePolicyPair(StoragePolicy sourcePolicy, StoragePolicy targetPolicy) {
        if (sourcePolicy.getId().equals(targetPolicy.getId())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "源存储策略和目标存储策略不能相同");
        }
        if (!targetPolicy.isEnabled()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目标存储策略必须处于启用状态");
        }
        if (sourcePolicy.getType() != targetPolicy.getType()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "当前只支持迁移同类型存储策略");
        }
        StoragePolicyType runtimeType = resolveRuntimePolicyType();
        if (runtimeType != null
                && (sourcePolicy.getType() != runtimeType || targetPolicy.getType() != runtimeType)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "当前运行时只支持迁移同类型活动存储后端的策略");
        }
    }

    private StoragePolicyType resolveRuntimePolicyType() {
        if (fileContentStorage instanceof LocalFileContentStorage) {
            return StoragePolicyType.LOCAL;
        }
        if (fileContentStorage instanceof S3FileContentStorage) {
            return StoragePolicyType.S3_COMPATIBLE;
        }
        return null;
    }

    private void validateTargetCapacity(FileEntity entity, StoragePolicy targetPolicy) {
        if (targetPolicy.getMaxSizeBytes() > 0 && entity.getSize() != null && entity.getSize() > targetPolicy.getMaxSizeBytes()) {
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

    private Map<String, Object> progressPatch(StoragePolicy sourcePolicy,
                                              StoragePolicy targetPolicy,
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
        patch.put("sourcePolicyId", sourcePolicy.getId());
        patch.put("sourcePolicyName", sourcePolicy.getName());
        patch.put("targetPolicyId", targetPolicy.getId());
        patch.put("targetPolicyName", targetPolicy.getName());
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

    private void scheduleStaleObjectCleanup(LinkedHashSet<String> staleObjectKeys) {
        if (staleObjectKeys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String staleObjectKey : staleObjectKeys) {
                    try {
                        fileContentStorage.deleteBlob(staleObjectKey);
                    } catch (RuntimeException ignored) {
                        // Database state already committed; leave old object cleanup as best effort.
                    }
                }
            }
        });
    }

    private void cleanupCopiedObjects(List<String> copiedObjectKeys) {
        for (String copiedObjectKey : copiedObjectKeys) {
            try {
                fileContentStorage.deleteBlob(copiedObjectKey);
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
