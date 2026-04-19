package com.yoyuzh.ops.admin.internal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.ops.admin.internal.web.AdminStoragePolicyMigrationCreateRequest;
import com.yoyuzh.ops.admin.internal.web.AdminStoragePolicyUpsertRequest;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyCredentialMode;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.policy.StoragePolicyType;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStorageGovernanceServiceTest {

    @Mock
    private StoragePolicyRepository storagePolicyRepository;
    @Mock
    private StoragePolicyService storagePolicyService;
    @Mock
    private FileEntityRepository fileEntityRepository;
    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;
    @Mock
    private BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminStorageGovernanceService adminStorageGovernanceService;

    @BeforeEach
    void setUp() {
        adminStorageGovernanceService = new AdminStorageGovernanceService(
                storagePolicyRepository,
                storagePolicyService,
                fileEntityRepository,
                storedFileEntityRepository,
                backgroundTaskLifecycleApi,
                adminAuditService
        );
    }

    @Test
    void shouldCreateStoragePolicy() {
        when(storagePolicyService.writeCapabilities(any(StoragePolicyCapabilities.class))).thenReturn("{\"maxObjectSize\":20480}");
        when(storagePolicyRepository.save(any(StoragePolicy.class))).thenAnswer(invocation -> {
            StoragePolicy policy = invocation.getArgument(0);
            policy.setId(9L);
            return policy;
        });
        when(storagePolicyService.readCapabilities(any(StoragePolicy.class))).thenReturn(defaultCapabilities(20_480L));

        AdminStoragePolicyResponse response = adminStorageGovernanceService.createStoragePolicy(new AdminStoragePolicyUpsertRequest(
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

        assertThat(response.name()).isEqualTo("Archive Bucket");
        assertThat(response.type()).isEqualTo(StoragePolicyType.S3_COMPATIBLE);
        assertThat(response.bucketName()).isEqualTo("archive-bucket");
        assertThat(response.endpoint()).isEqualTo("https://s3.example.com");
        assertThat(response.region()).isEqualTo("auto");
        assertThat(response.privateBucket()).isTrue();
        assertThat(response.prefix()).isEqualTo("archive/");
        assertThat(response.credentialMode()).isEqualTo(StoragePolicyCredentialMode.STATIC);
        assertThat(response.maxSizeBytes()).isEqualTo(20_480L);
        assertThat(response.enabled()).isTrue();
        assertThat(response.defaultPolicy()).isFalse();
    }

    @Test
    void shouldUpdateStoragePolicyFieldsWithoutChangingDefaultFlag() {
        StoragePolicy existingPolicy = createStoragePolicy(7L, "Archive Bucket");
        existingPolicy.setDefaultPolicy(false);
        when(storagePolicyService.writeCapabilities(any(StoragePolicyCapabilities.class))).thenReturn("{\"maxObjectSize\":40960}");
        when(storagePolicyRepository.findById(7L)).thenReturn(Optional.of(existingPolicy));
        when(storagePolicyRepository.save(existingPolicy)).thenReturn(existingPolicy);
        when(storagePolicyService.readCapabilities(existingPolicy)).thenReturn(defaultCapabilities(40_960L));

        AdminStoragePolicyResponse response = adminStorageGovernanceService.updateStoragePolicy(7L, new AdminStoragePolicyUpsertRequest(
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

        assertThat(existingPolicy.getName()).isEqualTo("Hot Bucket");
        assertThat(existingPolicy.getBucketName()).isEqualTo("hot-bucket");
        assertThat(existingPolicy.getEndpoint()).isEqualTo("https://hot.example.com");
        assertThat(existingPolicy.getRegion()).isEqualTo("cn-north-1");
        assertThat(existingPolicy.isPrivateBucket()).isFalse();
        assertThat(existingPolicy.getPrefix()).isEqualTo("hot/");
        assertThat(existingPolicy.getCredentialMode()).isEqualTo(StoragePolicyCredentialMode.DOGECLOUD_TEMP);
        assertThat(existingPolicy.getMaxSizeBytes()).isEqualTo(40_960L);
        assertThat(existingPolicy.isEnabled()).isTrue();
        assertThat(response.defaultPolicy()).isFalse();
    }

    @Test
    void shouldRejectDisablingDefaultStoragePolicy() {
        StoragePolicy existingPolicy = createStoragePolicy(3L, "Default Local Storage");
        existingPolicy.setDefaultPolicy(true);
        existingPolicy.setEnabled(true);
        when(storagePolicyRepository.findById(3L)).thenReturn(Optional.of(existingPolicy));

        assertThatThrownBy(() -> adminStorageGovernanceService.updateStoragePolicyStatus(3L, false))
                .isInstanceOf(BusinessException.class);

        verify(storagePolicyRepository, never()).save(any(StoragePolicy.class));
    }

    @Test
    void shouldCreateStoragePolicyMigrationTaskSkeleton() throws Exception {
        User adminUser = createUser(99L, "alice", "alice@example.com");
        StoragePolicy sourcePolicy = createStoragePolicy(3L, "Source Policy");
        StoragePolicy targetPolicy = createStoragePolicy(4L, "Target Policy");
        targetPolicy.setEnabled(true);
        when(storagePolicyRepository.findById(3L)).thenReturn(Optional.of(sourcePolicy));
        when(storagePolicyRepository.findById(4L)).thenReturn(Optional.of(targetPolicy));
        when(fileEntityRepository.countByStoragePolicyIdAndEntityType(3L, FileEntityType.VERSION)).thenReturn(5L);
        when(storedFileEntityRepository.countDistinctStoredFilesByStoragePolicyIdAndEntityType(3L, FileEntityType.VERSION)).thenReturn(8L);
        when(backgroundTaskLifecycleApi.createQueuedTask(eq(adminUser), eq(BackgroundTaskType.STORAGE_POLICY_MIGRATION), any(), any(), eq("migration-1")))
                .thenAnswer(invocation -> new BackgroundTaskView(
                        11L,
                        BackgroundTaskType.STORAGE_POLICY_MIGRATION,
                        BackgroundTaskStatus.QUEUED,
                        adminUser.getId(),
                        new ObjectMapper().writeValueAsString(invocation.getArgument(2)),
                        "migration-1",
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        null
                ));

        BackgroundTaskView task = adminStorageGovernanceService.createStoragePolicyMigrationTask(adminUser, new AdminStoragePolicyMigrationCreateRequest(
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
    }

    private User createUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setRole(UserRole.ADMIN);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private StoragePolicy createStoragePolicy(Long id, String name) {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(id);
        policy.setName(name);
        policy.setType(StoragePolicyType.S3_COMPATIBLE);
        policy.setBucketName("bucket");
        policy.setEndpoint("https://s3.example.com");
        policy.setRegion("auto");
        policy.setPrivateBucket(true);
        policy.setPrefix("files/");
        policy.setCredentialMode(StoragePolicyCredentialMode.STATIC);
        policy.setMaxSizeBytes(10_240L);
        policy.setCapabilitiesJson("{}");
        policy.setEnabled(true);
        policy.setDefaultPolicy(false);
        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());
        return policy;
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
