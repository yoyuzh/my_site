package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationApi;
import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationItem;
import com.yoyuzh.platform.storage.api.StoragePolicyBlobAccessApi;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    private ContentStoragePolicyMigrationApi contentStoragePolicyMigrationApi;
    @Mock
    private StoragePolicyBlobAccessApi storagePolicyBlobAccessApi;

    private StoragePolicyMigrationBackgroundTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StoragePolicyMigrationBackgroundTaskHandler(
                storagePolicyQuery,
                contentStoragePolicyMigrationApi,
                storagePolicyBlobAccessApi,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
    }

    @Test
    void shouldMigrateCandidateEntitiesAndUpdatePolicyCounts() {
        StoragePolicyDescriptor sourcePolicy = createPolicy(3L, "Source Policy");
        StoragePolicyDescriptor targetPolicy = createPolicy(4L, "Target Policy");
        ContentStoragePolicyMigrationItem entity = createMigrationItem(21L, 30L);
        when(storagePolicyQuery.readPolicyDescriptor(3L)).thenReturn(sourcePolicy);
        when(storagePolicyQuery.readPolicyDescriptor(4L)).thenReturn(targetPolicy);
        when(contentStoragePolicyMigrationApi.listVersionItemsByStoragePolicyId(3L)).thenReturn(List.of(entity));
        when(storagePolicyBlobAccessApi.readBlob(sourcePolicy, "blobs/source-1")).thenReturn("payload".getBytes());

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
        verify(contentStoragePolicyMigrationApi).reassignVersionItem(eq(21L), eq(30L), eq(4L), startsWith("policies/4/blobs/"));
        verify(storagePolicyBlobAccessApi).storeBlob(eq(targetPolicy), startsWith("policies/4/blobs/"), eq("video/mp4"), any());
    }

    @Test
    void shouldDeleteCopiedObjectsWhenMigrationFails() {
        StoragePolicyDescriptor sourcePolicy = createPolicy(3L, "Source Policy");
        StoragePolicyDescriptor targetPolicy = createPolicy(4L, "Target Policy");
        ContentStoragePolicyMigrationItem entity = createMigrationItem(21L, 30L);
        when(storagePolicyQuery.readPolicyDescriptor(3L)).thenReturn(sourcePolicy);
        when(storagePolicyQuery.readPolicyDescriptor(4L)).thenReturn(targetPolicy);
        when(contentStoragePolicyMigrationApi.listVersionItemsByStoragePolicyId(3L)).thenReturn(List.of(entity));
        when(storagePolicyBlobAccessApi.readBlob(sourcePolicy, "blobs/source-1")).thenReturn("payload".getBytes());
        doThrow(new IllegalStateException("store failed")).when(storagePolicyBlobAccessApi)
                .storeBlob(eq(targetPolicy), startsWith("policies/4/blobs/"), eq("video/mp4"), any());

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
        verify(storagePolicyBlobAccessApi).deleteBlob(eq(targetPolicy), startsWith("policies/4/blobs/"));
    }

    private ContentStoragePolicyMigrationItem createMigrationItem(Long entityId, Long blobId) {
        return new ContentStoragePolicyMigrationItem(
                entityId,
                "blobs/source-1",
                12L,
                "video/mp4",
                blobId,
                "video/mp4",
                12L,
                2L,
                ContentStoragePolicyMigrationApi.VERSION_ENTITY_TYPE
        );
    }

    private StoragePolicyDescriptor createPolicy(Long id, String name) {
        return new StoragePolicyDescriptor(
                id,
                name,
                StoragePolicyType.LOCAL,
                null,
                null,
                null,
                true,
                "/tmp/storage-" + id,
                StoragePolicyCredentialMode.NONE,
                true,
                0L
        );
    }
}
