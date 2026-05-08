package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminMetricsState;
import com.yoyuzh.ops.admin.internal.infra.AdminMetricsStateRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointRepository;
import com.yoyuzh.files.workspace.api.WorkspaceAdminMetricsApi;
import com.yoyuzh.files.sharing.api.SharingAdminMetricsApi;
import com.yoyuzh.platform.job.api.BackgroundTaskAdminQueryApi;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceTest {

    @Mock
    private AdminMetricsStateRepository adminMetricsStateRepository;
    @Mock
    private AdminRequestTimelinePointRepository adminRequestTimelinePointRepository;
    @Mock
    private AdminDailyActiveUserRepository adminDailyActiveUserRepository;
    @Mock
    private WorkspaceAdminMetricsApi workspaceAdminMetricsApi;
    @Mock
    private SharingAdminMetricsApi sharingAdminMetricsApi;
    @Mock
    private BackgroundTaskAdminQueryApi backgroundTaskAdminQueryApi;
    @Mock
    private EntityManager entityManager;

    private AdminMetricsService adminMetricsService;

    @BeforeEach
    void setUp() {
        adminMetricsService = new AdminMetricsService(
                adminMetricsStateRepository,
                adminRequestTimelinePointRepository,
                adminDailyActiveUserRepository,
                workspaceAdminMetricsApi,
                sharingAdminMetricsApi,
                backgroundTaskAdminQueryApi,
                entityManager
        );
    }

    @Test
    void shouldResetDailyRequestCountWhenSnapshotReadsPreviousDayState() {
        AdminMetricsState state = new AdminMetricsState();
        state.setId(1L);
        state.setRequestCount(42L);
        state.setRequestCountDate(LocalDate.now().minusDays(1));
        state.setOfflineTransferStorageLimitBytes(20L * 1024 * 1024 * 1024);

        when(adminMetricsStateRepository.findById(1L)).thenReturn(Optional.of(state));
        when(adminMetricsStateRepository.save(any(AdminMetricsState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adminRequestTimelinePointRepository.findAllByMetricDateOrderByHourAsc(LocalDate.now())).thenReturn(java.util.List.of());
        when(adminDailyActiveUserRepository.findAllByMetricDateBetweenOrderByMetricDateAscUsernameAsc(LocalDate.now().minusDays(6), LocalDate.now()))
                .thenReturn(java.util.List.of());
        when(workspaceAdminMetricsApi.countFavoriteFilesAsAdmin()).thenReturn(3L);
        when(sharingAdminMetricsApi.totalDownloadCountAsAdmin()).thenReturn(7L);
        when(backgroundTaskAdminQueryApi.countActiveTasks()).thenReturn(2L);

        AdminMetricsSnapshot snapshot = adminMetricsService.getSnapshot();

        assertThat(snapshot.requestCount()).isZero();
        assertThat(snapshot.favoriteFileCount()).isEqualTo(3L);
        assertThat(snapshot.shareDownloadCount()).isEqualTo(7L);
        assertThat(snapshot.activeTaskCount()).isEqualTo(2L);
        assertThat(state.getRequestCount()).isZero();
        assertThat(state.getRequestCountDate()).isEqualTo(LocalDate.now());
        assertThat(snapshot.requestTimeline()).hasSize(LocalTime.now().getHour() + 1);
        assertThat(snapshot.requestTimeline().get(0)).isEqualTo(new AdminRequestTimelinePoint(0, "00:00", 0L));
        assertThat(snapshot.dailyActiveUsers()).hasSize(7);
        assertThat(snapshot.dailyActiveUsers().get(6).metricDate()).isEqualTo(LocalDate.now());
        assertThat(snapshot.dailyActiveUsers().get(6).userCount()).isZero();
        verify(adminMetricsStateRepository).save(state);
        verify(adminDailyActiveUserRepository).deleteAllByMetricDateBefore(LocalDate.now().minusDays(6));
    }

    @Test
    void shouldStartNewDayRequestCountAtOneWhenIncrementingPreviousDayState() {
        when(adminMetricsStateRepository.incrementRequestCountBy(eq(1L), eq(LocalDate.now()), eq(1L), any())).thenReturn(1);
        when(adminRequestTimelinePointRepository.incrementRequestCount(eq(LocalDate.now()), eq(LocalTime.now().getHour()), eq(1L), any()))
                .thenReturn(0)
                .thenReturn(0);
        when(adminMetricsStateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(createCurrentState(LocalDate.now())));
        when(adminRequestTimelinePointRepository.save(any(AdminRequestTimelinePointEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adminMetricsService.incrementRequestCount();
        adminMetricsService.flushPendingMetrics();

        verify(adminMetricsStateRepository).incrementRequestCountBy(eq(1L), eq(LocalDate.now()), eq(1L), any());
        verify(adminRequestTimelinePointRepository).save(any(AdminRequestTimelinePointEntity.class));
    }

    @Test
    void shouldInitializeMetricsStateWithoutFlushInsertPath() {
        when(adminMetricsStateRepository.incrementRequestCountBy(eq(1L), eq(LocalDate.now()), eq(1L), any()))
                .thenReturn(0)
                .thenReturn(1);
        when(adminMetricsStateRepository.insertIfAbsent(eq(1L), eq(0L), eq(LocalDate.now()), eq(0L), eq(0L), eq(20L * 1024 * 1024 * 1024), any()))
                .thenReturn(1);
        when(adminRequestTimelinePointRepository.incrementRequestCount(eq(LocalDate.now()), eq(LocalTime.now().getHour()), eq(1L), any()))
                .thenReturn(1);

        adminMetricsService.incrementRequestCount();
        adminMetricsService.flushPendingMetrics();

        verify(adminMetricsStateRepository, never()).save(any(AdminMetricsState.class));
    }

    @Test
    void shouldCreateMissingTimelinePointWithoutFlushInsertPath() {
        when(adminMetricsStateRepository.incrementRequestCountBy(eq(1L), eq(LocalDate.now()), eq(1L), any())).thenReturn(1);
        when(adminRequestTimelinePointRepository.incrementRequestCount(eq(LocalDate.now()), eq(LocalTime.now().getHour()), eq(1L), any()))
                .thenReturn(0)
                .thenReturn(0);
        when(adminMetricsStateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(createCurrentState(LocalDate.now())));
        when(adminRequestTimelinePointRepository.save(any(AdminRequestTimelinePointEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adminMetricsService.incrementRequestCount();
        adminMetricsService.flushPendingMetrics();

        verify(adminRequestTimelinePointRepository, times(2))
                .incrementRequestCount(eq(LocalDate.now()), eq(LocalTime.now().getHour()), eq(1L), any());
        verify(adminMetricsStateRepository).findByIdForUpdate(1L);
        verify(adminRequestTimelinePointRepository).save(any(AdminRequestTimelinePointEntity.class));
    }

    @Test
    void shouldIncrementDownloadTrafficAtomically() {
        when(adminMetricsStateRepository.incrementDownloadTrafficBytes(eq(1L), eq(1024L), any())).thenReturn(1);

        adminMetricsService.recordDownloadTraffic(1024L);
        adminMetricsService.flushPendingMetrics();

        verify(adminMetricsStateRepository).incrementDownloadTrafficBytes(eq(1L), eq(1024L), any());
        verify(adminMetricsStateRepository, never()).findByIdForUpdate(1L);
    }

    @Test
    void shouldRecordUniqueDailyActiveUserAndBuildSevenDayHistory() {
        LocalDate today = LocalDate.now();
        AdminDailyActiveUserEntity existing = new AdminDailyActiveUserEntity();
        existing.setMetricDate(today);
        existing.setUserId(7L);
        existing.setUsername("alice");

        AdminDailyActiveUserEntity yesterday = new AdminDailyActiveUserEntity();
        yesterday.setMetricDate(today.minusDays(1));
        yesterday.setUserId(8L);
        yesterday.setUsername("bob");

        when(adminDailyActiveUserRepository.findAllByMetricDateBetweenOrderByMetricDateAscUsernameAsc(today.minusDays(6), today))
                .thenReturn(java.util.List.of(yesterday, existing));
        when(adminMetricsStateRepository.findById(1L)).thenReturn(Optional.of(createCurrentState(today)));
        when(adminRequestTimelinePointRepository.findAllByMetricDateOrderByHourAsc(today)).thenReturn(java.util.List.of());
        when(workspaceAdminMetricsApi.countFavoriteFilesAsAdmin()).thenReturn(0L);
        when(sharingAdminMetricsApi.totalDownloadCountAsAdmin()).thenReturn(0L);
        when(backgroundTaskAdminQueryApi.countActiveTasks()).thenReturn(0L);

        adminMetricsService.recordUserOnline(7L, "alice");
        adminMetricsService.recordUserOnline(7L, "alice");
        AdminMetricsSnapshot snapshot = adminMetricsService.getSnapshot();

        assertThat(snapshot.dailyActiveUsers()).hasSize(7);
        assertThat(snapshot.dailyActiveUsers().get(5).metricDate()).isEqualTo(today.minusDays(1));
        assertThat(snapshot.dailyActiveUsers().get(5).userCount()).isEqualTo(1L);
        assertThat(snapshot.dailyActiveUsers().get(5).usernames()).containsExactly("bob");
        assertThat(snapshot.dailyActiveUsers().get(6).metricDate()).isEqualTo(today);
        assertThat(snapshot.dailyActiveUsers().get(6).userCount()).isEqualTo(1L);
        assertThat(snapshot.dailyActiveUsers().get(6).usernames()).containsExactly("alice");
        verify(adminDailyActiveUserRepository).insertIfAbsent(today, 7L, "alice");
        verify(adminDailyActiveUserRepository, never()).save(any(AdminDailyActiveUserEntity.class));
        verify(adminDailyActiveUserRepository, atMostOnce()).deleteAllByMetricDateBefore(today.minusDays(6));
    }

    @Test
    void shouldInsertPendingDailyActiveUserWithoutImmediateReadBack() {
        LocalDate today = LocalDate.now();

        when(adminDailyActiveUserRepository.insertIfAbsent(today, 7L, "alice")).thenReturn(1);

        adminMetricsService.recordUserOnline(7L, "alice");
        adminMetricsService.flushPendingMetrics();

        verify(adminDailyActiveUserRepository).insertIfAbsent(today, 7L, "alice");
        verify(adminDailyActiveUserRepository, never()).findByMetricDateAndUserId(today, 7L);
        verify(adminDailyActiveUserRepository, never()).findByMetricDateAndUserIdForUpdate(today, 7L);
        verify(adminDailyActiveUserRepository, never()).save(any(AdminDailyActiveUserEntity.class));
    }

    @Test
    void shouldContinueFlushingOtherDailyActiveUsersWhenOneInsertFails() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-01T05:10:00Z"), ZoneOffset.UTC);
        LocalDate today = LocalDate.now(fixedClock);
        adminMetricsService = new AdminMetricsService(
                adminMetricsStateRepository,
                adminRequestTimelinePointRepository,
                adminDailyActiveUserRepository,
                workspaceAdminMetricsApi,
                sharingAdminMetricsApi,
                backgroundTaskAdminQueryApi,
                entityManager,
                fixedClock
        );

        doThrow(new IllegalStateException("db unavailable"))
                .when(adminDailyActiveUserRepository)
                .insertIfAbsent(today, 7L, "alice");
        when(adminDailyActiveUserRepository.insertIfAbsent(today, 8L, "bob")).thenReturn(1);

        adminMetricsService.recordUserOnline(7L, "alice");
        adminMetricsService.recordUserOnline(8L, "bob");

        adminMetricsService.flushPendingMetrics();

        verify(adminDailyActiveUserRepository).insertIfAbsent(today, 7L, "alice");
        verify(adminDailyActiveUserRepository).insertIfAbsent(today, 8L, "bob");

        adminMetricsService.flushPendingMetrics();

        verify(adminDailyActiveUserRepository, times(2)).insertIfAbsent(today, 7L, "alice");
        verify(adminDailyActiveUserRepository, times(1)).insertIfAbsent(today, 8L, "bob");
    }

    private AdminMetricsState createCurrentState(LocalDate metricDate) {
        AdminMetricsState state = new AdminMetricsState();
        state.setId(1L);
        state.setRequestCount(0L);
        state.setRequestCountDate(metricDate);
        state.setOfflineTransferStorageLimitBytes(20L * 1024 * 1024 * 1024);
        return state;
    }
}
