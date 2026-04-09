package com.yoyuzh.admin;

import com.yoyuzh.auth.PasswordPolicy;
import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.RefreshTokenService;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyCredentialMode;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.policy.StoragePolicyType;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskService;
import com.yoyuzh.files.tasks.BackgroundTaskStatus;
import com.yoyuzh.files.tasks.BackgroundTaskType;
import com.yoyuzh.transfer.OfflineTransferSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private FileService fileService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private RegistrationInviteService registrationInviteService;
    @Mock
    private OfflineTransferSessionRepository offlineTransferSessionRepository;
    @Mock
    private AdminMetricsService adminMetricsService;
    @Mock
    private StoragePolicyRepository storagePolicyRepository;
    @Mock
    private StoragePolicyService storagePolicyService;
    @Mock
    private FileEntityRepository fileEntityRepository;
    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;
    @Mock
    private BackgroundTaskService backgroundTaskService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository, storedFileRepository, fileBlobRepository, fileService,
                passwordEncoder, refreshTokenService, registrationInviteService,
                offlineTransferSessionRepository, adminMetricsService,
                storagePolicyRepository, storagePolicyService,
                fileEntityRepository, storedFileEntityRepository, backgroundTaskService);
    }

    // --- getSummary ---

    @Test
    void shouldReturnSummaryWithCountsAndInviteCode() {
        when(userRepository.count()).thenReturn(5L);
        when(storedFileRepository.count()).thenReturn(42L);
        when(fileBlobRepository.sumAllBlobSize()).thenReturn(8192L);
        when(adminMetricsService.getSnapshot()).thenReturn(new AdminMetricsSnapshot(
                0L,
                0L,
                0L,
                20L * 1024 * 1024 * 1024,
                List.of(
                        new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate().minusDays(1), "昨天", 1L, List.of("alice")),
                        new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate(), "今天", 2L, List.of("alice", "bob"))
                ),
                List.of(
                        new AdminRequestTimelinePoint(0, "00:00", 0L),
                        new AdminRequestTimelinePoint(1, "01:00", 3L)
                )
        ));
        when(offlineTransferSessionRepository.sumUploadedFileSizeByExpiresAtAfter(any())).thenReturn(0L);
        when(registrationInviteService.getCurrentInviteCode()).thenReturn("INV-001");

        AdminSummaryResponse summary = adminService.getSummary();

        assertThat(summary.totalUsers()).isEqualTo(5L);
        assertThat(summary.totalFiles()).isEqualTo(42L);
        assertThat(summary.totalStorageBytes()).isEqualTo(8192L);
        assertThat(summary.downloadTrafficBytes()).isZero();
        assertThat(summary.requestCount()).isZero();
        assertThat(summary.transferUsageBytes()).isZero();
        assertThat(summary.offlineTransferStorageBytes()).isZero();
        assertThat(summary.offlineTransferStorageLimitBytes()).isGreaterThan(0L);
        assertThat(summary.dailyActiveUsers()).containsExactly(
                new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate().minusDays(1), "昨天", 1L, List.of("alice")),
                new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate(), "今天", 2L, List.of("alice", "bob"))
        );
        assertThat(summary.requestTimeline()).containsExactly(
                new AdminRequestTimelinePoint(0, "00:00", 0L),
                new AdminRequestTimelinePoint(1, "01:00", 3L)
        );
        assertThat(summary.inviteCode()).isEqualTo("INV-001");
    }

    // --- listUsers ---

    @Test
    void shouldListUsersWithPagination() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.searchByUsernameOrEmail(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(storedFileRepository.sumFileSizeByUserId(1L)).thenReturn(2048L);

        PageResponse<AdminUserResponse> response = adminService.listUsers(0, 10, "alice");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).username()).isEqualTo("alice");
        assertThat(response.items().get(0).usedStorageBytes()).isEqualTo(2048L);
    }

    @Test
    void shouldNormalizeNullQueryToEmptyStringWhenListingUsers() {
        when(userRepository.searchByUsernameOrEmail(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        adminService.listUsers(0, 10, null);

        verify(userRepository).searchByUsernameOrEmail(eq(""), any());
    }

    // --- listFiles ---

    @Test
    void shouldListFilesWithPagination() {
        User owner = createUser(1L, "alice", "alice@example.com");
        StoredFile file = createFile(10L, owner, "/docs", "report.pdf");
        when(storedFileRepository.searchAdminFiles(anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(file)));

        PageResponse<AdminFileResponse> response = adminService.listFiles(0, 10, "report", "alice");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).filename()).isEqualTo("report.pdf");
        assertThat(response.items().get(0).ownerUsername()).isEqualTo("alice");
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

        AdminStoragePolicyResponse response = adminService.createStoragePolicy(new AdminStoragePolicyUpsertRequest(
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

        AdminStoragePolicyResponse response = adminService.updateStoragePolicy(7L, new AdminStoragePolicyUpsertRequest(
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

        assertThatThrownBy(() -> adminService.updateStoragePolicyStatus(3L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("默认存储策略不能停用");

        verify(storagePolicyRepository, never()).save(any(StoragePolicy.class));
    }

    @Test
    void shouldCreateStoragePolicyMigrationTaskSkeleton() {
        User adminUser = createUser(99L, "alice", "alice@example.com");
        StoragePolicy sourcePolicy = createStoragePolicy(3L, "Source Policy");
        StoragePolicy targetPolicy = createStoragePolicy(4L, "Target Policy");
        targetPolicy.setEnabled(true);
        when(storagePolicyRepository.findById(3L)).thenReturn(Optional.of(sourcePolicy));
        when(storagePolicyRepository.findById(4L)).thenReturn(Optional.of(targetPolicy));
        when(fileEntityRepository.countByStoragePolicyIdAndEntityType(3L, com.yoyuzh.files.core.FileEntityType.VERSION)).thenReturn(5L);
        when(storedFileEntityRepository.countDistinctStoredFilesByStoragePolicyIdAndEntityType(3L, com.yoyuzh.files.core.FileEntityType.VERSION)).thenReturn(8L);
        when(backgroundTaskService.createQueuedTask(eq(adminUser), eq(BackgroundTaskType.STORAGE_POLICY_MIGRATION), any(), any(), eq("migration-1")))
                .thenAnswer(invocation -> {
                    BackgroundTask task = new BackgroundTask();
                    task.setId(11L);
                    task.setType(BackgroundTaskType.STORAGE_POLICY_MIGRATION);
                    task.setStatus(BackgroundTaskStatus.QUEUED);
                    task.setUserId(adminUser.getId());
                    task.setPublicStateJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(invocation.getArgument(2)));
                    task.setPrivateStateJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(invocation.getArgument(3)));
                    task.setCorrelationId("migration-1");
                    task.setCreatedAt(LocalDateTime.now());
                    task.setUpdatedAt(LocalDateTime.now());
                    return task;
                });

        BackgroundTask task = adminService.createStoragePolicyMigrationTask(adminUser, new AdminStoragePolicyMigrationCreateRequest(
                3L,
                4L,
                "migration-1"
        ));

        assertThat(task.getType()).isEqualTo(BackgroundTaskType.STORAGE_POLICY_MIGRATION);
        assertThat(task.getStatus()).isEqualTo(BackgroundTaskStatus.QUEUED);
        assertThat(task.getPublicStateJson()).contains("\"sourcePolicyId\":3");
        assertThat(task.getPublicStateJson()).contains("\"targetPolicyId\":4");
        assertThat(task.getPublicStateJson()).contains("\"candidateEntityCount\":5");
        assertThat(task.getPublicStateJson()).contains("\"candidateStoredFileCount\":8");
        assertThat(task.getPublicStateJson()).contains("\"migrationPerformed\":false");
        assertThat(task.getPrivateStateJson()).contains("\"taskType\":\"STORAGE_POLICY_MIGRATION\"");
    }

    // --- deleteFile ---

    @Test
    void shouldDeleteFileByDelegatingToFileService() {
        User owner = createUser(1L, "alice", "alice@example.com");
        StoredFile file = createFile(10L, owner, "/docs", "report.pdf");
        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(file));

        adminService.deleteFile(10L);

        verify(fileService).delete(owner, 10L);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentFile() {
        when(storedFileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deleteFile(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件不存在");
    }

    // --- updateUserRole ---

    @Test
    void shouldUpdateUserRole() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserResponse response = adminService.updateUserRole(1L, UserRole.ADMIN);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowWhenUpdatingRoleForNonExistentUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateUserRole(99L, UserRole.ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    // --- updateUserBanned ---

    @Test
    void shouldBanUserAndRevokeTokens() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        adminService.updateUserBanned(1L, true);

        assertThat(user.isBanned()).isTrue();
        verify(refreshTokenService).revokeAllForUser(1L);
        verify(userRepository).save(user);
    }

    @Test
    void shouldUnbanUserAndRevokeExistingTokens() {
        User user = createUser(1L, "alice", "alice@example.com");
        user.setBanned(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        adminService.updateUserBanned(1L, false);

        assertThat(user.isBanned()).isFalse();
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    // --- updateUserPassword ---

    @Test
    void shouldUpdateUserPasswordAndRevokeTokens() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewStr0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(user)).thenReturn(user);

        adminService.updateUserPassword(1L, "NewStr0ng!Pass");

        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void shouldRejectWeakPasswordWhenUpdating() {
        assertThatThrownBy(() -> adminService.updateUserPassword(1L, "weakpass"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码至少8位");
        verify(userRepository, never()).findById(any());
    }

    // --- resetUserPassword ---

    @Test
    void shouldResetUserPasswordAndReturnTemporaryPassword() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(user)).thenReturn(user);

        AdminPasswordResetResponse response = adminService.resetUserPassword(1L);

        assertThat(response.temporaryPassword()).isNotBlank();
        assertThat(PasswordPolicy.isStrong(response.temporaryPassword())).isTrue();
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    // --- helpers ---

    private User createUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private StoredFile createFile(Long id, User owner, String path, String filename) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(owner);
        file.setPath(path);
        file.setFilename(filename);
        file.setSize(1024L);
        file.setDirectory(false);
        file.setCreatedAt(LocalDateTime.now());
        return file;
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
