package com.yoyuzh.ops.admin.internal.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.files.sharing.api.SharingAdminShareSnapshot;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileSnapshot;
import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import com.yoyuzh.shared.kernel.BusinessException;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminResourceGovernanceServiceTest {

    @Mock
    private WorkspaceAdminGovernanceApi workspaceAdminGovernanceApi;
    @Mock
    private SharingApi sharingApi;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminResourceGovernanceService adminResourceGovernanceService;

    @BeforeEach
    void setUp() {
        adminResourceGovernanceService = new AdminResourceGovernanceService(
                workspaceAdminGovernanceApi,
                sharingApi,
                adminAuditService
        );
    }

    @Test
    void shouldDeleteShareViaSharingApi() {
        when(sharingApi.deleteShareAsAdmin(5L))
                .thenReturn(Optional.of(new SharingAdminShareSnapshot(5L, "secret-token")));

        adminResourceGovernanceService.deleteShare(5L);

        verify(sharingApi).deleteShareAsAdmin(5L);
        verify(adminAuditService).record(
                eq(AdminAuditAction.DELETE_SHARE),
                eq("SHARE"),
                eq(5L),
                eq("Deleted share link"),
                argThat(details -> Objects.equals(details.get("token"), "secret-token"))
        );
    }

    @Test
    void shouldThrowWhenDeletingNonExistentShare() {
        when(sharingApi.deleteShareAsAdmin(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminResourceGovernanceService.deleteShare(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("share not found");
    }

    @Test
    void shouldDeleteFileViaWorkspaceApi() {
        WorkspaceAdminFileSnapshot fileSnapshot = new WorkspaceAdminFileSnapshot(
                10L,
                1L,
                "/docs",
                "report.pdf",
                false
        );
        when(workspaceAdminGovernanceApi.deleteFileAsAdmin(10L)).thenReturn(Optional.of(fileSnapshot));

        adminResourceGovernanceService.deleteFile(10L);

        verify(workspaceAdminGovernanceApi).deleteFileAsAdmin(10L);
        verify(adminAuditService).record(
                eq(AdminAuditAction.DELETE_FILE),
                eq("FILE"),
                eq(10L),
                eq("Deleted file"),
                argThat(details ->
                        Objects.equals(details.get("ownerUserId"), 1L)
                                && Objects.equals(details.get("path"), "/docs")
                                && Objects.equals(details.get("filename"), "report.pdf")
                                && Objects.equals(details.get("directory"), false))
        );
    }

    @Test
    void shouldThrowWhenDeletingNonExistentFile() {
        when(workspaceAdminGovernanceApi.deleteFileAsAdmin(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminResourceGovernanceService.deleteFile(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("file not found");
    }
}
