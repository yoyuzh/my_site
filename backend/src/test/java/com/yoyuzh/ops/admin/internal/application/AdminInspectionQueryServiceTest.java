package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.files.content.api.ContentAdminFileBlobQuery;
import com.yoyuzh.files.content.api.ContentAdminFileBlobView;
import com.yoyuzh.files.content.api.ContentAdminInspectionApi;
import com.yoyuzh.files.content.api.ContentEntityType;
import com.yoyuzh.identity.access.api.IdentityAdminSummaryApi;
import com.yoyuzh.ops.admin.api.AdminFileBlobResponse;
import com.yoyuzh.ops.admin.api.AdminFileResponse;
import com.yoyuzh.ops.admin.api.AdminShareResponse;
import com.yoyuzh.files.sharing.api.SharingAdminShareQuery;
import com.yoyuzh.files.sharing.api.SharingAdminShareView;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileQuery;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileView;
import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.transfer.api.TransferAdminMetricsApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInspectionQueryServiceTest {

    @Mock
    private IdentityAdminSummaryApi identityAdminSummaryApi;
    @Mock
    private TransferAdminMetricsApi transferAdminMetricsApi;
    @Mock
    private AdminMetricsService adminMetricsService;
    @Mock
    private SharingApi sharingApi;
    @Mock
    private WorkspaceAdminGovernanceApi workspaceAdminGovernanceApi;
    @Mock
    private ContentAdminInspectionApi contentAdminInspectionApi;

    private AdminInspectionQueryService adminInspectionQueryService;

    @BeforeEach
    void setUp() {
        adminInspectionQueryService = new AdminInspectionQueryService(
                identityAdminSummaryApi,
                transferAdminMetricsApi,
                adminMetricsService,
                contentAdminInspectionApi,
                sharingApi,
                workspaceAdminGovernanceApi
        );
    }

    @Test
    void shouldReturnSummaryWithCountsAndInviteCode() {
        when(identityAdminSummaryApi.countUsersAsAdmin()).thenReturn(5L);
        when(workspaceAdminGovernanceApi.countFilesAsAdmin()).thenReturn(42L);
        when(contentAdminInspectionApi.totalBlobSize()).thenReturn(8192L);
        when(adminMetricsService.getSnapshot()).thenReturn(new AdminMetricsSnapshot(
                0L,
                0L,
                0L,
                20L * 1024 * 1024 * 1024,
                List.of(
                        new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate().minusDays(1), "yesterday", 1L, List.of("alice")),
                        new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate(), "today", 2L, List.of("alice", "bob"))
                ),
                List.of(
                        new AdminRequestTimelinePoint(0, "00:00", 0L),
                        new AdminRequestTimelinePoint(1, "01:00", 3L)
                )
        ));
        when(transferAdminMetricsApi.currentOfflineStorageBytes()).thenReturn(0L);
        when(identityAdminSummaryApi.currentInviteCode()).thenReturn("INV-001");

        AdminSummaryResponse summary = adminInspectionQueryService.getSummary();

        assertThat(summary.totalUsers()).isEqualTo(5L);
        assertThat(summary.totalFiles()).isEqualTo(42L);
        assertThat(summary.totalStorageBytes()).isEqualTo(8192L);
        assertThat(summary.downloadTrafficBytes()).isZero();
        assertThat(summary.requestCount()).isZero();
        assertThat(summary.transferUsageBytes()).isZero();
        assertThat(summary.offlineTransferStorageBytes()).isZero();
        assertThat(summary.offlineTransferStorageLimitBytes()).isGreaterThan(0L);
        assertThat(summary.dailyActiveUsers()).containsExactly(
                new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate().minusDays(1), "yesterday", 1L, List.of("alice")),
                new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate(), "today", 2L, List.of("alice", "bob"))
        );
        assertThat(summary.requestTimeline()).containsExactly(
                new AdminRequestTimelinePoint(0, "00:00", 0L),
                new AdminRequestTimelinePoint(1, "01:00", 3L)
        );
        assertThat(summary.inviteCode()).isEqualTo("INV-001");
    }

    @Test
    void shouldListFilesWithPagination() {
        WorkspaceAdminFileView file = new WorkspaceAdminFileView(
                10L,
                "report.pdf",
                "/docs",
                1024L,
                "application/pdf",
                false,
                LocalDateTime.now(),
                1L,
                "alice",
                "alice@example.com"
        );
        when(workspaceAdminGovernanceApi.listFilesAsAdmin(any(WorkspaceAdminFileQuery.class)))
                .thenReturn(new PageResponse<>(List.of(file), 1L, 0, 10));

        PageResponse<AdminFileResponse> response = adminInspectionQueryService.listFiles(0, 10, "report", "alice");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).filename()).isEqualTo("report.pdf");
        assertThat(response.items().get(0).ownerUsername()).isEqualTo("alice");
    }

    @Test
    void shouldListFileBlobsWithBatchLoadedBlobAndLinkStats() {
        ContentAdminFileBlobView blobView = new ContentAdminFileBlobView(
                100L,
                88L,
                "blobs/a",
                ContentEntityType.VERSION,
                5L,
                1024L,
                "application/pdf",
                1,
                1L,
                1L,
                "alice",
                "alice@example.com",
                9L,
                "creator",
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().minusMinutes(3),
                false,
                false,
                false
        );
        when(contentAdminInspectionApi.listFileBlobsAsAdmin(any(ContentAdminFileBlobQuery.class)))
                .thenReturn(new PageResponse<>(List.of(blobView), 1L, 0, 10));

        PageResponse<AdminFileBlobResponse> response = adminInspectionQueryService.listFileBlobs(
                0,
                10,
                null,
                null,
                null,
                null
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).entityId()).isEqualTo(100L);
        assertThat(response.items().get(0).blobId()).isEqualTo(88L);
        assertThat(response.items().get(0).linkedStoredFileCount()).isEqualTo(1L);
        assertThat(response.items().get(0).linkedOwnerCount()).isEqualTo(1L);
        assertThat(response.items().get(0).sampleOwnerUsername()).isEqualTo("alice");
        assertThat(response.items().get(0).sampleOwnerEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldListSharesViaSharingApi() {
        SharingAdminShareView share = new SharingAdminShareView(
                5L,
                "secret-token",
                "report-share",
                true,
                false,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(1),
                10,
                2L,
                4L,
                true,
                true,
                1L,
                "alice",
                "alice@example.com",
                10L,
                "report.pdf",
                "/docs",
                "application/pdf",
                1024L,
                false
        );
        when(sharingApi.listSharesAsAdmin(any(SharingAdminShareQuery.class)))
                .thenReturn(new PageResponse<>(List.of(share), 1L, 0, 10));

        PageResponse<AdminShareResponse> response = adminInspectionQueryService.listShares(
                0,
                10,
                "alice",
                "report",
                "secret",
                true,
                false
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(5L);
        assertThat(response.items().get(0).ownerUsername()).isEqualTo("alice");
        assertThat(response.items().get(0).fileName()).isEqualTo("report.pdf");
    }
}
