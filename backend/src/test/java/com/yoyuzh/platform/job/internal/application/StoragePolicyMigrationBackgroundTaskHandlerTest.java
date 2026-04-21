package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private StoragePolicyQuery storagePolicyQuery;
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
                storagePolicyQuery,
                fileEntityRepository,
                fileBlobRepository,
                storedFileRepository,
                fileContentStorage,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
    }

    @Test
    void shouldMigrateCandidateEntitiesAndUpdatePolicyCounts() {
        StoragePolicyDescriptor sourcePolicy = createPolicy(3L, "Source Policy");
        StoragePolicyDescriptor targetPolicy = createPolicy(4L, "Target Policy");
        FileBlob blob = new FileBlob();
        blob.setId(30L);
        blob.setObjectKey("blobs/source-1");
        blob.setContentType("video/mp4");
        blob.setSize(12L);
        var entity = new com.yoyuzh.files.content.internal.domain.FileEntity();
        entity.setId(21L);
        entity.setObjectKey("blobs/source-1");
        entity.setContentType("video/mp4");
        entity.setSize(12L);
        entity.setEntityType(FileEntityType.VERSION);
        entity.setStoragePolicyId(3L);
        when(storagePolicyQuery.readPolicyDescriptor(3L)).thenReturn(sourcePolicy);
        when(storagePolicyQuery.readPolicyDescriptor(4L)).thenReturn(targetPolicy);
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
        StoragePolicyDescriptor sourcePolicy = createPolicy(3L, "Source Policy");
        StoragePolicyDescriptor targetPolicy = createPolicy(4L, "Target Policy");
        FileBlob blob = new FileBlob();
        blob.setId(30L);
        blob.setObjectKey("blobs/source-1");
        blob.setContentType("video/mp4");
        blob.setSize(12L);
        var entity = new com.yoyuzh.files.content.internal.domain.FileEntity();
        entity.setId(21L);
        entity.setObjectKey("blobs/source-1");
        entity.setContentType("video/mp4");
        entity.setSize(12L);
        entity.setEntityType(FileEntityType.VERSION);
        entity.setStoragePolicyId(3L);
        when(storagePolicyQuery.readPolicyDescriptor(3L)).thenReturn(sourcePolicy);
        when(storagePolicyQuery.readPolicyDescriptor(4L)).thenReturn(targetPolicy);
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

    private StoragePolicyDescriptor createPolicy(Long id, String name) {
        return new StoragePolicyDescriptor(
                id,
                name,
                StoragePolicyType.LOCAL,
                true,
                0L
        );
    }
}
