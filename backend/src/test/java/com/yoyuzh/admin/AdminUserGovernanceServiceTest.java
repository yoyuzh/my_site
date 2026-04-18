package com.yoyuzh.admin;

import com.yoyuzh.auth.AuthSessionPolicy;
import com.yoyuzh.auth.AuthTokenInvalidationService;
import com.yoyuzh.auth.PasswordPolicy;
import com.yoyuzh.auth.RefreshTokenService;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserGovernanceServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuthTokenInvalidationService authTokenInvalidationService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private AdminRuntimeSettingsService adminRuntimeSettingsService;

    private AuthSessionPolicy authSessionPolicy;
    private AdminUserGovernanceService adminUserGovernanceService;

    @BeforeEach
    void setUp() {
        authSessionPolicy = new AuthSessionPolicy();
        adminUserGovernanceService = new AdminUserGovernanceService(
                userRepository,
                storedFileRepository,
                passwordEncoder,
                refreshTokenService,
                authTokenInvalidationService,
                authSessionPolicy,
                adminAuditService,
                adminRuntimeSettingsService
        );
    }

    @Test
    void shouldListUsersWithPagination() {
        User user = createUser(1L, "alice", "alice@example.com");
        StoredFileRepository.UserStorageUsageProjection usageProjection = mock(StoredFileRepository.UserStorageUsageProjection.class);
        when(usageProjection.getUserId()).thenReturn(1L);
        when(usageProjection.getUsedStorageBytes()).thenReturn(2048L);
        when(userRepository.searchByUsernameOrEmail(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(storedFileRepository.sumFileSizeByUserIds(any())).thenReturn(List.of(usageProjection));

        PageResponse<AdminUserResponse> response = adminUserGovernanceService.listUsers(0, 10, "alice");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).username()).isEqualTo("alice");
        assertThat(response.items().get(0).usedStorageBytes()).isEqualTo(2048L);
    }

    @Test
    void shouldNormalizeNullQueryToEmptyStringWhenListingUsers() {
        when(userRepository.searchByUsernameOrEmail(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        adminUserGovernanceService.listUsers(0, 10, null);

        verify(userRepository).searchByUsernameOrEmail(eq(""), any());
    }

    @Test
    void shouldUpdateUserRole() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("MODERATOR", "ADMIN"));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserResponse response = adminUserGovernanceService.updateUserRole(1L, UserRole.MODERATOR);

        assertThat(user.getRole()).isEqualTo(UserRole.MODERATOR);
        assertThat(response.role()).isEqualTo(UserRole.MODERATOR);
        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowWhenUpdatingRoleForNonExistentUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserGovernanceService.updateUserRole(99L, UserRole.ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("user not found");
    }

    @Test
    void shouldBanUserAndRevokeTokens() {
        User user = createUser(1L, "alice", "alice@example.com");
        String previousActiveSessionId = user.getActiveSessionId();
        String previousDesktopSessionId = user.getDesktopActiveSessionId();
        String previousMobileSessionId = user.getMobileActiveSessionId();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("MODERATOR", "ADMIN"));
        when(userRepository.save(user)).thenReturn(user);

        adminUserGovernanceService.updateUserBanned(1L, true);

        assertThat(user.isBanned()).isTrue();
        assertThat(user.getActiveSessionId()).isNotEqualTo(previousActiveSessionId);
        assertThat(user.getDesktopActiveSessionId()).isNotEqualTo(previousDesktopSessionId);
        assertThat(user.getMobileActiveSessionId()).isNotEqualTo(previousMobileSessionId);
        verify(authTokenInvalidationService).revokeAccessTokensForUser(1L);
        verify(refreshTokenService).revokeAllForUser(1L);
        verify(userRepository).save(user);
    }

    @Test
    void shouldUnbanUserAndRevokeExistingTokens() {
        User user = createUser(1L, "alice", "alice@example.com");
        user.setBanned(true);
        String previousActiveSessionId = user.getActiveSessionId();
        String previousDesktopSessionId = user.getDesktopActiveSessionId();
        String previousMobileSessionId = user.getMobileActiveSessionId();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("MODERATOR", "ADMIN"));
        when(userRepository.save(user)).thenReturn(user);

        adminUserGovernanceService.updateUserBanned(1L, false);

        assertThat(user.isBanned()).isFalse();
        assertThat(user.getActiveSessionId()).isNotEqualTo(previousActiveSessionId);
        assertThat(user.getDesktopActiveSessionId()).isNotEqualTo(previousDesktopSessionId);
        assertThat(user.getMobileActiveSessionId()).isNotEqualTo(previousMobileSessionId);
        verify(authTokenInvalidationService).revokeAccessTokensForUser(1L);
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void shouldUpdateUserPasswordAndRevokeTokens() {
        User user = createUser(1L, "alice", "alice@example.com");
        String previousActiveSessionId = user.getActiveSessionId();
        String previousDesktopSessionId = user.getDesktopActiveSessionId();
        String previousMobileSessionId = user.getMobileActiveSessionId();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewStr0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(user)).thenReturn(user);

        adminUserGovernanceService.updateUserPassword(1L, "NewStr0ng!Pass");

        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getActiveSessionId()).isNotEqualTo(previousActiveSessionId);
        assertThat(user.getDesktopActiveSessionId()).isNotEqualTo(previousDesktopSessionId);
        assertThat(user.getMobileActiveSessionId()).isNotEqualTo(previousMobileSessionId);
        verify(authTokenInvalidationService).revokeAccessTokensForUser(1L);
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void shouldRejectWeakPasswordWhenUpdating() {
        assertThatThrownBy(() -> adminUserGovernanceService.updateUserPassword(1L, "weakpass"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PasswordPolicy.VALIDATION_MESSAGE);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void shouldUpdateUserStorageQuota() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserResponse response = adminUserGovernanceService.updateUserStorageQuota(1L, 1234L);

        assertThat(user.getStorageQuotaBytes()).isEqualTo(1234L);
        assertThat(response.storageQuotaBytes()).isEqualTo(1234L);
    }

    @Test
    void shouldUpdateUserMaxUploadSize() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserResponse response = adminUserGovernanceService.updateUserMaxUploadSize(1L, 5678L);

        assertThat(user.getMaxUploadSizeBytes()).isEqualTo(5678L);
        assertThat(response.maxUploadSizeBytes()).isEqualTo(5678L);
    }

    @Test
    void shouldResetUserPasswordAndReturnTemporaryPassword() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(user)).thenReturn(user);

        AdminPasswordResetResponse response = adminUserGovernanceService.resetUserPassword(1L);

        assertThat(response.temporaryPassword()).isNotBlank();
        assertThat(PasswordPolicy.isStrong(response.temporaryPassword())).isTrue();
        verify(authTokenInvalidationService).revokeAccessTokensForUser(1L);
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void shouldRejectDemotingLastAdminCapableUser() {
        User user = createUser(1L, "alice", "alice@example.com");
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("ADMIN"));
        when(userRepository.countByBannedFalseAndRoleIn(anyCollection())).thenReturn(1L);

        assertThatThrownBy(() -> adminUserGovernanceService.updateUserRole(1L, UserRole.USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one unbanned admin-capable user must remain");

        verify(userRepository, never()).save(user);
    }

    @Test
    void shouldAllowDemotingAdminWhenAnotherAdminCapableUserRemains() {
        User user = createUser(1L, "alice", "alice@example.com");
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.countByBannedFalseAndRoleIn(anyCollection())).thenReturn(2L);
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("ADMIN"));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserResponse response = adminUserGovernanceService.updateUserRole(1L, UserRole.USER);

        assertThat(response.role()).isEqualTo(UserRole.USER);
        verify(userRepository).save(user);
    }

    @Test
    void shouldRejectBanningLastAdminCapableUser() {
        User user = createUser(1L, "alice", "alice@example.com");
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("ADMIN"));
        when(userRepository.countByBannedFalseAndRoleIn(anyCollection())).thenReturn(1L);

        assertThatThrownBy(() -> adminUserGovernanceService.updateUserBanned(1L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one unbanned admin-capable user must remain");

        verify(userRepository, never()).save(user);
    }

    private User createUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setRole(UserRole.USER);
        user.setActiveSessionId("active-session-" + id);
        user.setDesktopActiveSessionId("desktop-session-" + id);
        user.setMobileActiveSessionId("mobile-session-" + id);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private AdminRuntimeSettingsService.State runtimeState(String... managementRoles) {
        return new AdminRuntimeSettingsService.State(
                false,
                true,
                List.of(managementRoles),
                900L,
                1209600L,
                false,
                60L,
                true,
                false,
                false,
                "in-memory",
                3000L,
                15000L,
                false,
                "local",
                false
        );
    }
}
