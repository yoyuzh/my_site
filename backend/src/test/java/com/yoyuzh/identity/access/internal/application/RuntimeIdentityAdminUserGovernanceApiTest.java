package com.yoyuzh.identity.access.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.identity.access.api.AdminAccessContinuityGuard;
import com.yoyuzh.identity.access.api.IdentityAdminUserQuery;
import com.yoyuzh.identity.access.api.IdentityAdminUserView;
import com.yoyuzh.identity.access.api.IdentityCredentialRevocationPolicy;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.PasswordPolicy;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RuntimeIdentityAdminUserGovernanceApiTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private IdentityCredentialRevocationPolicy identityCredentialRevocationPolicy;
    @Mock
    private AdminRuntimeSettingsService adminRuntimeSettingsService;

    private AdminAccessContinuityGuard adminAccessContinuityGuard;
    private RuntimeIdentityAdminUserGovernanceApi runtimeIdentityAdminUserGovernanceApi;

    @BeforeEach
    void setUp() {
        adminAccessContinuityGuard =
                new RuntimeAdminAccessContinuityGuard(userRepository, adminRuntimeSettingsService);
        runtimeIdentityAdminUserGovernanceApi = new RuntimeIdentityAdminUserGovernanceApi(
                userRepository,
                passwordEncoder,
                identityCredentialRevocationPolicy,
                adminAccessContinuityGuard
        );
    }

    @Test
    void shouldListUsersWithPagination() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.searchByUsernameOrEmail(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(user)));

        PageResponse<IdentityAdminUserView> response = runtimeIdentityAdminUserGovernanceApi
                .listUsersAsAdmin(new IdentityAdminUserQuery(0, 10, "alice"));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).username()).isEqualTo("alice");
        assertThat(response.items().get(0).role()).isEqualTo(IdentityRoleName.USER);
    }

    @Test
    void shouldNormalizeNullQueryToEmptyStringWhenListingUsers() {
        when(userRepository.searchByUsernameOrEmail(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        runtimeIdentityAdminUserGovernanceApi.listUsersAsAdmin(new IdentityAdminUserQuery(0, 10, null));

        verify(userRepository).searchByUsernameOrEmail(eq(""), any());
    }

    @Test
    void shouldUpdateUserRole() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("MODERATOR", "ADMIN"));
        when(userRepository.save(user)).thenReturn(user);

        IdentityAdminUserView response =
                runtimeIdentityAdminUserGovernanceApi.updateUserRoleAsAdmin(1L, IdentityRoleName.MODERATOR);

        assertThat(user.getRole()).isEqualTo(UserRole.MODERATOR);
        assertThat(response.role()).isEqualTo(IdentityRoleName.MODERATOR);
        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowWhenUpdatingRoleForNonExistentUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runtimeIdentityAdminUserGovernanceApi.updateUserRoleAsAdmin(99L, IdentityRoleName.ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("user not found");
    }

    @Test
    void shouldBanUserAndRevokeTokens() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("MODERATOR", "ADMIN"));
        when(userRepository.save(user)).thenReturn(user);

        runtimeIdentityAdminUserGovernanceApi.updateUserBannedAsAdmin(1L, true);

        assertThat(user.isBanned()).isTrue();
        verify(identityCredentialRevocationPolicy).revokeAll(user);
        verify(userRepository).save(user);
    }

    @Test
    void shouldUnbanUserAndRevokeExistingTokens() {
        User user = createUser(1L, "alice", "alice@example.com");
        user.setBanned(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("MODERATOR", "ADMIN"));
        when(userRepository.save(user)).thenReturn(user);

        runtimeIdentityAdminUserGovernanceApi.updateUserBannedAsAdmin(1L, false);

        assertThat(user.isBanned()).isFalse();
        verify(identityCredentialRevocationPolicy).revokeAll(user);
    }

    @Test
    void shouldUpdateUserPasswordAndRevokeTokens() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewStr0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(user)).thenReturn(user);

        runtimeIdentityAdminUserGovernanceApi.updateUserPasswordAsAdmin(1L, "NewStr0ng!Pass");

        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        verify(identityCredentialRevocationPolicy).revokeAll(user);
    }

    @Test
    void shouldRejectWeakPasswordWhenUpdating() {
        assertThatThrownBy(() -> runtimeIdentityAdminUserGovernanceApi.updateUserPasswordAsAdmin(1L, "weakpass"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PasswordPolicy.VALIDATION_MESSAGE);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void shouldUpdateUserStorageQuota() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        IdentityAdminUserView response = runtimeIdentityAdminUserGovernanceApi.updateUserStorageQuotaAsAdmin(1L, 1234L);

        assertThat(user.getStorageQuotaBytes()).isEqualTo(1234L);
        assertThat(response.storageQuotaBytes()).isEqualTo(1234L);
    }

    @Test
    void shouldUpdateUserMaxUploadSize() {
        User user = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        IdentityAdminUserView response = runtimeIdentityAdminUserGovernanceApi.updateUserMaxUploadSizeAsAdmin(1L, 5678L);

        assertThat(user.getMaxUploadSizeBytes()).isEqualTo(5678L);
        assertThat(response.maxUploadSizeBytes()).isEqualTo(5678L);
    }

    @Test
    void shouldRejectDemotingLastAdminCapableUser() {
        User user = createUser(1L, "alice", "alice@example.com");
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("ADMIN"));
        when(userRepository.countByBannedFalseAndRoleIn(anyCollection())).thenReturn(1L);

        assertThatThrownBy(() -> runtimeIdentityAdminUserGovernanceApi.updateUserRoleAsAdmin(1L, IdentityRoleName.USER))
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

        IdentityAdminUserView response = runtimeIdentityAdminUserGovernanceApi.updateUserRoleAsAdmin(1L, IdentityRoleName.USER);

        assertThat(response.role()).isEqualTo(IdentityRoleName.USER);
        verify(userRepository).save(user);
    }

    @Test
    void shouldRejectBanningLastAdminCapableUser() {
        User user = createUser(1L, "alice", "alice@example.com");
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("ADMIN"));
        when(userRepository.countByBannedFalseAndRoleIn(anyCollection())).thenReturn(1L);

        assertThatThrownBy(() -> runtimeIdentityAdminUserGovernanceApi.updateUserBannedAsAdmin(1L, true))
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
