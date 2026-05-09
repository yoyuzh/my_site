package com.yoyuzh.ops.admin.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import com.yoyuzh.identity.access.api.IdentityAdminUserGovernanceApi;
import com.yoyuzh.identity.access.api.IdentityAdminUserQuery;
import com.yoyuzh.identity.access.api.IdentityAdminUserView;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.ops.admin.api.AdminPasswordResetResponse;
import com.yoyuzh.ops.admin.api.AdminUserResponse;
import com.yoyuzh.ops.admin.api.AdminUserRole;
import com.yoyuzh.shared.kernel.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserGovernanceServiceTest {

    @Mock
    private IdentityAdminUserGovernanceApi identityAdminUserGovernanceApi;
    @Mock
    private WorkspaceAdminGovernanceApi workspaceAdminGovernanceApi;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminUserGovernanceService adminUserGovernanceService;

    @BeforeEach
    void setUp() {
        adminUserGovernanceService = new AdminUserGovernanceService(
                identityAdminUserGovernanceApi,
                workspaceAdminGovernanceApi,
                adminAuditService
        );
    }

    @Test
    void shouldListUsersWithUsedStorageFromWorkspaceApi() {
        IdentityAdminUserView alice =
                createUser(1L, "alice", "alice@example.com", IdentityRoleName.MODERATOR, false);
        when(identityAdminUserGovernanceApi.listUsersAsAdmin(any(IdentityAdminUserQuery.class)))
                .thenReturn(new PageResponse<>(List.of(alice), 1L, 0, 10));
        when(workspaceAdminGovernanceApi.loadUsedStorageBytesByUserIds(Map.of(1L, 0L).keySet()))
                .thenReturn(Map.of(1L, 2048L));

        PageResponse<AdminUserResponse> response = adminUserGovernanceService.listUsers(0, 10, "  alice  ");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).username()).isEqualTo("alice");
        assertThat(response.items().get(0).role()).isEqualTo(AdminUserRole.MODERATOR);
        assertThat(response.items().get(0).usedStorageBytes()).isEqualTo(2048L);
        ArgumentCaptor<IdentityAdminUserQuery> queryCaptor = ArgumentCaptor.forClass(IdentityAdminUserQuery.class);
        verify(identityAdminUserGovernanceApi).listUsersAsAdmin(queryCaptor.capture());
        assertThat(queryCaptor.getValue().query()).isEqualTo("alice");
        verify(workspaceAdminGovernanceApi).loadUsedStorageBytesByUserIds(Map.of(1L, 0L).keySet());
    }

    @Test
    void shouldUpdateUserRoleAndAudit() {
        IdentityAdminUserView updated =
                createUser(1L, "alice", "alice@example.com", IdentityRoleName.ADMIN, false);
        when(identityAdminUserGovernanceApi.updateUserRoleAsAdmin(1L, IdentityRoleName.ADMIN)).thenReturn(updated);
        when(workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(1L)).thenReturn(1024L);

        AdminUserResponse response = adminUserGovernanceService.updateUserRole(1L, AdminUserRole.ADMIN);

        assertThat(response.role()).isEqualTo(AdminUserRole.ADMIN);
        assertThat(response.usedStorageBytes()).isEqualTo(1024L);
        verify(adminAuditService).record(
                eq(AdminAuditAction.USER_ROLE_UPDATED),
                eq("USER"),
                eq(1L),
                eq("Updated user role"),
                eq(Map.of("role", "ADMIN"))
        );
    }

    @Test
    void shouldUpdateUserBannedAndAudit() {
        IdentityAdminUserView updated =
                createUser(1L, "alice", "alice@example.com", IdentityRoleName.USER, true);
        when(identityAdminUserGovernanceApi.updateUserBannedAsAdmin(1L, true)).thenReturn(updated);
        when(workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(1L)).thenReturn(99L);

        AdminUserResponse response = adminUserGovernanceService.updateUserBanned(1L, true);

        assertThat(response.banned()).isTrue();
        verify(adminAuditService).record(
                eq(AdminAuditAction.USER_STATUS_UPDATED),
                eq("USER"),
                eq(1L),
                eq("Banned user"),
                eq(Map.of("banned", true))
        );
    }

    @Test
    void shouldUpdateUserPasswordAndAuditDetails() {
        IdentityAdminUserView updated =
                createUser(1L, "alice", "alice@example.com", IdentityRoleName.USER, false);
        when(identityAdminUserGovernanceApi.updateUserPasswordAsAdmin(1L, "NewStr0ng!Pass")).thenReturn(updated);
        when(workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(1L)).thenReturn(10L);

        adminUserGovernanceService.updateUserPassword(1L, "NewStr0ng!Pass");

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(adminAuditService).record(
                eq(AdminAuditAction.USER_PASSWORD_UPDATED),
                eq("USER"),
                eq(1L),
                eq("Updated user password"),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue()).doesNotContainKey("passwordLength");
        assertThat(detailsCaptor.getValue()).containsEntry("temporaryPassword", false);
    }

    @Test
    void shouldResetPasswordAndMarkTemporaryInAudit() {
        IdentityAdminUserView updated =
                createUser(2L, "bob", "bob@example.com", IdentityRoleName.USER, false);
        when(identityAdminUserGovernanceApi.updateUserPasswordAsAdmin(eq(2L), any(String.class))).thenReturn(updated);
        when(workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(2L)).thenReturn(0L);

        AdminPasswordResetResponse response = adminUserGovernanceService.resetUserPassword(2L);

        assertThat(response.temporaryPassword()).hasSize(16);
        assertThat(response.temporaryPassword()).matches(".*[A-Z].*");
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(adminAuditService).record(
                eq(AdminAuditAction.USER_PASSWORD_RESET),
                eq("USER"),
                eq(2L),
                eq("Reset user password"),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue()).containsEntry("temporaryPassword", true);
        assertThat(detailsCaptor.getValue()).doesNotContainKey("passwordLength");
    }

    @Test
    void shouldUpdateUserStorageQuotaAndAudit() {
        IdentityAdminUserView updated =
                createUser(1L, "alice", "alice@example.com", IdentityRoleName.USER, false);
        when(identityAdminUserGovernanceApi.updateUserStorageQuotaAsAdmin(1L, 2048L)).thenReturn(updated);
        when(workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(1L)).thenReturn(512L);

        AdminUserResponse response = adminUserGovernanceService.updateUserStorageQuota(1L, 2048L);

        assertThat(response.storageQuotaBytes()).isEqualTo(2048L);
        verify(adminAuditService).record(
                eq(AdminAuditAction.USER_STORAGE_QUOTA_UPDATED),
                eq("USER"),
                eq(1L),
                eq("Updated user storage quota"),
                eq(Map.of("storageQuotaBytes", 2048L))
        );
    }

    @Test
    void shouldUpdateUserMaxUploadSizeAndAudit() {
        IdentityAdminUserView updated =
                createUser(1L, "alice", "alice@example.com", IdentityRoleName.USER, false);
        when(identityAdminUserGovernanceApi.updateUserMaxUploadSizeAsAdmin(1L, 4096L)).thenReturn(updated);
        when(workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(1L)).thenReturn(512L);

        AdminUserResponse response = adminUserGovernanceService.updateUserMaxUploadSize(1L, 4096L);

        assertThat(response.maxUploadSizeBytes()).isEqualTo(4096L);
        verify(adminAuditService).record(
                eq(AdminAuditAction.USER_MAX_UPLOAD_SIZE_UPDATED),
                eq("USER"),
                eq(1L),
                eq("Updated user max upload size"),
                eq(Map.of("maxUploadSizeBytes", 4096L))
        );
    }

    private IdentityAdminUserView createUser(
            Long id,
            String username,
            String email,
            IdentityRoleName role,
            boolean banned) {
        return new IdentityAdminUserView(
                id,
                username,
                email,
                "13900139000",
                LocalDateTime.now(),
                role,
                banned,
                2048L,
                4096L
        );
    }
}
