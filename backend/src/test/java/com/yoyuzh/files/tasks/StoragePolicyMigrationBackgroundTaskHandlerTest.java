package com.yoyuzh.files.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyType;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoragePolicyMigrationBackgroundTaskHandlerTest {

    @Mock
    private StoragePolicyRepository storagePolicyRepository;
    @Mock
    private FileEntityRepository fileEntityRepository;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileContentStorage fileContentStorage;

    private StoragePolicyMigrationBackgroundTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StoragePolicyMigrationBackgroundTaskHandler(
                storagePolicyRepository,
                fileEntityRepository,
                fileBlobRepository,
                storedFileRepository,
                fileContentStorage,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
    }

    @Test
    void shouldMigrateCandidateEntitiesAndUpdatePolicyCounts() {
        StoragePolicy sourcePolicy = createPolicy(3L, "Source Policy");
        StoragePolicy targetPolicy = createPolicy(4L, "Target Policy");
        FileBlob blob = new FileBlob();
        blob.setId(30L);
        blob.setObjectKey("blobs/source-1");
        blob.setContentType("video/mp4");
        blob.setSize(12L);
        var entity = new com.yoyuzh.files.core.FileEntity();
        entity.setId(21L);
        entity.setObjectKey("blobs/source-1");
        entity.setContentType("video/mp4");
        entity.setSize(12L);
        entity.setEntityType(FileEntityType.VERSION);
        entity.setStoragePolicyId(3L);
        when(storagePolicyRepository.findById(3L)).thenReturn(Optional.of(sourcePolicy));
        when(storagePolicyRepository.findById(4L)).thenReturn(Optional.of(targetPolicy));
        when(fileEntityRepository.findByStoragePolicyIdAndEntityTypeOrderByIdAsc(3L, FileEntityType.VERSION)).thenReturn(List.of(entity));
        when(fileBlobRepository.findByObjectKey("blobs/source-1")).thenReturn(Optional.of(blob));
        when(storedFileRepository.countByBlobId(30L)).thenReturn(2L);
        when(fileEntityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileBlobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileContentStorage.readBlob("blobs/source-1")).thenReturn("payload".getBytes());

        BackgroundTask task = new BackgroundTask();
        task.setId(11L);
        task.setType(BackgroundTaskType.STORAGE_POLICY_MIGRATION);
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setUserId(99L);
        task.setPrivateStateJson("""
                {"sourcePolicyId":3,"targetPolicyId":4,"sourcePolicyName":"Source Policy","targetPolicyName":"Target Policy"}
                """);
        task.setPublicStateJson("{}");

        BackgroundTaskHandlerResult result = handler.handle(task);

        assertThat(result.publicStatePatch()).containsEntry("worker", "storage-policy-migration");
        assertThat(result.publicStatePatch()).containsEntry("migrationMode", "executed");
        assertThat(result.publicStatePatch()).containsEntry("migrationPerformed", true);
        assertThat(result.publicStatePatch()).containsEntry("candidateEntityCount", 1L);
        assertThat(result.publicStatePatch()).containsEntry("candidateStoredFileCount", 2L);
        assertThat(result.publicStatePatch()).containsEntry("migratedEntityCount", 1L);
        assertThat(result.publicStatePatch()).containsEntry("migratedStoredFileCount", 2L);
        assertThat(result.publicStatePatch()).containsEntry("processedEntityCount", 1L);
        assertThat(result.publicStatePatch()).containsEntry("progressPercent", 100);
        assertThat(entity.getStoragePolicyId()).isEqualTo(4L);
        assertThat(entity.getObjectKey()).startsWith("policies/4/blobs/");
        assertThat(blob.getObjectKey()).startsWith("policies/4/blobs/");
        verify(fileContentStorage).storeBlob(startsWith("policies/4/blobs/"), eq("video/mp4"), any());
    }

    @Test
    void shouldDeleteCopiedObjectsWhenMigrationFails() {
        StoragePolicy sourcePolicy = createPolicy(3L, "Source Policy");
        StoragePolicy targetPolicy = createPolicy(4L, "Target Policy");
        FileBlob blob = new FileBlob();
        blob.setId(30L);
        blob.setObjectKey("blobs/source-1");
        blob.setContentType("video/mp4");
        blob.setSize(12L);
        var entity = new com.yoyuzh.files.core.FileEntity();
        entity.setId(21L);
        entity.setObjectKey("blobs/source-1");
        entity.setContentType("video/mp4");
        entity.setSize(12L);
        entity.setEntityType(FileEntityType.VERSION);
        entity.setStoragePolicyId(3L);
        when(storagePolicyRepository.findById(3L)).thenReturn(Optional.of(sourcePolicy));
        when(storagePolicyRepository.findById(4L)).thenReturn(Optional.of(targetPolicy));
        when(fileEntityRepository.findByStoragePolicyIdAndEntityTypeOrderByIdAsc(3L, FileEntityType.VERSION)).thenReturn(List.of(entity));
        when(fileBlobRepository.findByObjectKey("blobs/source-1")).thenReturn(Optional.of(blob));
        when(storedFileRepository.countByBlobId(30L)).thenReturn(2L);
        when(fileContentStorage.readBlob("blobs/source-1")).thenReturn("payload".getBytes());
        doThrow(new IllegalStateException("store failed")).when(fileContentStorage).storeBlob(startsWith("policies/4/blobs/"), eq("video/mp4"), any());

        BackgroundTask task = new BackgroundTask();
        task.setId(11L);
        task.setType(BackgroundTaskType.STORAGE_POLICY_MIGRATION);
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setUserId(99L);
        task.setPrivateStateJson("""
                {"sourcePolicyId":3,"targetPolicyId":4,"sourcePolicyName":"Source Policy","targetPolicyName":"Target Policy"}
                """);
        task.setPublicStateJson("{}");

        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("store failed");
        verify(fileContentStorage).deleteBlob(startsWith("policies/4/blobs/"));
    }

    private StoragePolicy createPolicy(Long id, String name) {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(id);
        policy.setName(name);
        policy.setType(StoragePolicyType.LOCAL);
        policy.setEnabled(true);
        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());
        return policy;
    }
}
