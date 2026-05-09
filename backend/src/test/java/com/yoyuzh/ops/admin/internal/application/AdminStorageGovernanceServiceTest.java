package com.yoyuzh.ops.admin.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminApi;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminView;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyMigrationCandidate;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminStorageGovernanceServiceTest {

    @Mock
    private StoragePolicyAdminApi storagePolicyAdminApi;
    @Mock
    private BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminStorageGovernanceService adminStorageGovernanceService;

    @BeforeEach
    void setUp() {
        adminStorageGovernanceService = new AdminStorageGovernanceService(
                storagePolicyAdminApi,
                backgroundTaskLifecycleApi,
                adminAuditService
        );
    }

    @Test
    void shouldCreateStoragePolicy() {
        when(storagePolicyAdminApi.createStoragePolicyAsAdmin(any()))
                .thenReturn(storagePolicyView(9L, "Archive Bucket", true, false));

        AdminStoragePolicyResponse response = adminStorageGovernanceService.createStoragePolicy(new AdminStoragePolicyUpsertInput(
                " Archive Bucket ",
                StoragePolicyType.S3_COMPATIBLE,
                "archive-bucket",
                "https://s3.example.com",
                "auto",
                true,
                "archive/",
                StoragePolicyCredentialMode.STATIC,
                20_480L,
                defaultCapabilities(20_480L),
                true
        ));

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.name()).isEqualTo("Archive Bucket");
        assertThat(response.enabled()).isTrue();
        verify(adminAuditService).record(
                eq(AdminAuditAction.STORAGE_POLICY_CREATED),
                eq("STORAGE_POLICY"),
                eq(9L),
                eq("Created storage policy"),
                eq(Map.of("name", "Archive Bucket", "enabled", true))
        );
    }

    @Test
    void shouldUpdateStoragePolicy() {
        when(storagePolicyAdminApi.updateStoragePolicyAsAdmin(eq(7L), any()))
                .thenReturn(storagePolicyView(
                        7L,
                        "Hot Bucket",
                        true,
                        false,
                        StoragePolicyCredentialMode.DOGECLOUD_TEMP));

        AdminStoragePolicyResponse response = adminStorageGovernanceService.updateStoragePolicy(7L, new AdminStoragePolicyUpsertInput(
                "Hot Bucket",
                StoragePolicyType.S3_COMPATIBLE,
                "hot-bucket",
                "https://hot.example.com",
                "cn-north-1",
                false,
                "hot/",
                StoragePolicyCredentialMode.DOGECLOUD_TEMP,
                40_960L,
                defaultCapabilities(40_960L),
                true
        ));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Hot Bucket");
        assertThat(response.credentialMode()).isEqualTo(StoragePolicyCredentialMode.DOGECLOUD_TEMP);
        verify(adminAuditService).record(
                eq(AdminAuditAction.STORAGE_POLICY_UPDATED),
                eq("STORAGE_POLICY"),
                eq(7L),
                eq("Updated storage policy"),
                eq(Map.of("name", "Hot Bucket", "enabled", true))
        );
    }

    @Test
    void shouldUpdateStoragePolicyStatus() {
        when(storagePolicyAdminApi.updateStoragePolicyStatusAsAdmin(3L, false))
                .thenReturn(storagePolicyView(3L, "Archive Bucket", false, false));

        AdminStoragePolicyResponse response = adminStorageGovernanceService.updateStoragePolicyStatus(3L, false);

        assertThat(response.enabled()).isFalse();
        verify(adminAuditService).record(
                eq(AdminAuditAction.STORAGE_POLICY_STATUS_UPDATED),
                eq("STORAGE_POLICY"),
                eq(3L),
                eq("Disabled storage policy"),
                eq(Map.of("enabled", false))
        );
    }

    @Test
    void shouldCreateStoragePolicyMigrationTaskSkeleton() throws Exception {
        Long userId = 99L;
        when(storagePolicyAdminApi.buildStoragePolicyMigrationCandidate(3L, 4L))
                .thenReturn(new StoragePolicyMigrationCandidate(3L, "Source Policy", 4L, "Target Policy", 5L, 8L, "VERSION"));
        when(backgroundTaskLifecycleApi.createQueuedTaskByUserId(eq(userId), eq(BackgroundTaskType.STORAGE_POLICY_MIGRATION), any(), any(), eq("migration-1")))
                .thenAnswer(invocation -> new BackgroundTaskView(
                        11L,
                        BackgroundTaskType.STORAGE_POLICY_MIGRATION,
                        BackgroundTaskStatus.QUEUED,
                        userId,
                        new ObjectMapper().writeValueAsString(invocation.getArgument(2)),
                        "migration-1",
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        null
                ));

        BackgroundTaskView task = adminStorageGovernanceService.createStoragePolicyMigrationTask(userId, new AdminStoragePolicyMigrationInput(
                3L,
                4L,
                "migration-1"
        ));

        assertThat(task.type()).isEqualTo(BackgroundTaskType.STORAGE_POLICY_MIGRATION);
        assertThat(task.status()).isEqualTo(BackgroundTaskStatus.QUEUED);
        assertThat(task.publicStateJson()).contains("\"sourcePolicyId\":3");
        assertThat(task.publicStateJson()).contains("\"targetPolicyId\":4");
        assertThat(task.publicStateJson()).contains("\"candidateEntityCount\":5");
        assertThat(task.publicStateJson()).contains("\"candidateStoredFileCount\":8");
        assertThat(task.publicStateJson()).contains("\"migrationPerformed\":false");
        verify(adminAuditService).record(
                eq(AdminAuditAction.STORAGE_POLICY_MIGRATION_REQUESTED),
                eq("STORAGE_POLICY"),
                eq(3L),
                eq("Requested storage policy migration"),
                eq(Map.of(
                        "sourcePolicyId", 3L,
                        "targetPolicyId", 4L,
                        "correlationId", "migration-1"
                ))
        );
    }

    private StoragePolicyAdminView storagePolicyView(Long id, String name, boolean enabled, boolean defaultPolicy) {
        return storagePolicyView(id, name, enabled, defaultPolicy, StoragePolicyCredentialMode.STATIC);
    }

    private StoragePolicyAdminView storagePolicyView(
            Long id,
            String name,
            boolean enabled,
            boolean defaultPolicy,
            StoragePolicyCredentialMode credentialMode) {
        return new StoragePolicyAdminView(
                id,
                name,
                StoragePolicyType.S3_COMPATIBLE,
                "bucket",
                "https://s3.example.com",
                "auto",
                true,
                "files/",
                credentialMode,
                10_240L,
                defaultCapabilities(10_240L),
                enabled,
                defaultPolicy,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private StoragePolicyCapabilities defaultCapabilities(long maxObjectSize) {
        return new StoragePolicyCapabilities(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                maxObjectSize
        );
    }
}
