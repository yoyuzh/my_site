package com.yoyuzh.admin;

import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.share.FileShareLinkRepository;
import com.yoyuzh.transfer.OfflineTransferSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInspectionQueryServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private RegistrationInviteService registrationInviteService;
    @Mock
    private OfflineTransferSessionRepository offlineTransferSessionRepository;
    @Mock
    private AdminMetricsService adminMetricsService;
    @Mock
    private FileEntityRepository fileEntityRepository;
    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;
    @Mock
    private FileShareLinkRepository fileShareLinkRepository;

    private AdminInspectionQueryService adminInspectionQueryService;

    @BeforeEach
    void setUp() {
        adminInspectionQueryService = new AdminInspectionQueryService(
                userRepository,
                storedFileRepository,
                fileBlobRepository,
                registrationInviteService,
                offlineTransferSessionRepository,
                adminMetricsService,
                fileEntityRepository,
                storedFileEntityRepository,
                fileShareLinkRepository
        );
    }

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
                        new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate().minusDays(1), "yesterday", 1L, List.of("alice")),
                        new AdminDailyActiveUserSummary(LocalDateTime.now().toLocalDate(), "today", 2L, List.of("alice", "bob"))
                ),
                List.of(
                        new AdminRequestTimelinePoint(0, "00:00", 0L),
                        new AdminRequestTimelinePoint(1, "01:00", 3L)
                )
        ));
        when(offlineTransferSessionRepository.sumUploadedFileSizeByExpiresAtAfter(any())).thenReturn(0L);
        when(registrationInviteService.getCurrentInviteCode()).thenReturn("INV-001");

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
        User owner = createUser(1L, "alice", "alice@example.com");
        StoredFile file = createFile(10L, owner, "/docs", "report.pdf");
        when(storedFileRepository.searchAdminFiles(anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(file)));

        PageResponse<AdminFileResponse> response = adminInspectionQueryService.listFiles(0, 10, "report", "alice");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).filename()).isEqualTo("report.pdf");
        assertThat(response.items().get(0).ownerUsername()).isEqualTo("alice");
    }

    @Test
    void shouldListFileBlobsWithBatchLoadedBlobAndLinkStats() {
        User creator = createUser(9L, "creator", "creator@example.com");
        FileEntity entity = new FileEntity();
        entity.setId(100L);
        entity.setObjectKey("blobs/a");
        entity.setEntityType(FileEntityType.VERSION);
        entity.setStoragePolicyId(5L);
        entity.setSize(1024L);
        entity.setContentType("application/pdf");
        entity.setReferenceCount(1);
        entity.setCreatedBy(creator);
        entity.setCreatedAt(LocalDateTime.now().minusMinutes(2));

        FileBlob blob = new FileBlob();
        blob.setId(88L);
        blob.setObjectKey("blobs/a");
        blob.setContentType("application/pdf");
        blob.setSize(1024L);
        blob.setCreatedAt(LocalDateTime.now().minusMinutes(3));

        StoredFileEntityRepository.FileEntityLinkStatsProjection linkStats = mock(StoredFileEntityRepository.FileEntityLinkStatsProjection.class);
        when(linkStats.getFileEntityId()).thenReturn(100L);
        when(linkStats.getLinkedStoredFileCount()).thenReturn(1L);
        when(linkStats.getLinkedOwnerCount()).thenReturn(1L);
        when(linkStats.getSampleOwnerUsername()).thenReturn("alice");
        when(linkStats.getSampleOwnerEmail()).thenReturn("alice@example.com");

        when(fileEntityRepository.searchAdminEntities(anyString(), any(), anyString(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(fileBlobRepository.findAllByObjectKeyIn(any())).thenReturn(List.of(blob));
        when(storedFileEntityRepository.findAdminLinkStatsByFileEntityIds(any())).thenReturn(List.of(linkStats));

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

        verify(fileBlobRepository, never()).findByObjectKey(anyString());
        verify(storedFileEntityRepository, never()).countByFileEntityId(any());
        verify(storedFileEntityRepository, never()).countDistinctOwnersByFileEntityId(any());
        verify(storedFileEntityRepository, never()).findSampleOwnerUsernameByFileEntityId(any());
        verify(storedFileEntityRepository, never()).findSampleOwnerEmailByFileEntityId(any());
    }

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
