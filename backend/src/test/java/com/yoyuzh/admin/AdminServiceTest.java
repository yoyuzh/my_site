package com.yoyuzh.admin;

import com.yoyuzh.auth.PasswordPolicy;
import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.RefreshTokenService;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.FileBlobRepository;
import com.yoyuzh.files.FileService;
import com.yoyuzh.files.StoredFile;
import com.yoyuzh.files.StoredFileRepository;
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

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository, storedFileRepository, fileBlobRepository, fileService,
                passwordEncoder, refreshTokenService, registrationInviteService,
                offlineTransferSessionRepository, adminMetricsService);
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
}
