package com.yoyuzh.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private AdminMetricsService adminMetricsService;

    @BeforeEach
    void setUp() {
        adminMetricsService = new AdminMetricsService(
                adminMetricsStateRepository,
                adminRequestTimelinePointRepository,
                adminDailyActiveUserRepository
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

        AdminMetricsSnapshot snapshot = adminMetricsService.getSnapshot();

        assertThat(snapshot.requestCount()).isZero();
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
        AdminMetricsState state = new AdminMetricsState();
        state.setId(1L);
        state.setRequestCount(42L);
        state.setRequestCountDate(LocalDate.now().minusDays(1));
        state.setOfflineTransferStorageLimitBytes(20L * 1024 * 1024 * 1024);

        when(adminMetricsStateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(state));
        when(adminMetricsStateRepository.save(any(AdminMetricsState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adminRequestTimelinePointRepository.findByMetricDateAndHourForUpdate(LocalDate.now(), LocalTime.now().getHour()))
                .thenReturn(Optional.empty());
        when(adminRequestTimelinePointRepository.save(any(AdminRequestTimelinePointEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(adminRequestTimelinePointRepository.saveAndFlush(any(AdminRequestTimelinePointEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adminMetricsService.incrementRequestCount();

        assertThat(state.getRequestCount()).isEqualTo(1L);
        assertThat(state.getRequestCountDate()).isEqualTo(LocalDate.now());
        verify(adminMetricsStateRepository).save(state);
        verify(adminRequestTimelinePointRepository).save(any(AdminRequestTimelinePointEntity.class));
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

        when(adminDailyActiveUserRepository.findByMetricDateAndUserIdForUpdate(today, 7L)).thenReturn(Optional.of(existing));
        when(adminDailyActiveUserRepository.findAllByMetricDateBetweenOrderByMetricDateAscUsernameAsc(today.minusDays(6), today))
                .thenReturn(java.util.List.of(yesterday, existing));
        when(adminMetricsStateRepository.findById(1L)).thenReturn(Optional.of(createCurrentState(today)));
        when(adminRequestTimelinePointRepository.findAllByMetricDateOrderByHourAsc(today)).thenReturn(java.util.List.of());

        adminMetricsService.recordUserOnline(7L, "alice");
        AdminMetricsSnapshot snapshot = adminMetricsService.getSnapshot();

        assertThat(snapshot.dailyActiveUsers()).hasSize(7);
        assertThat(snapshot.dailyActiveUsers().get(5).metricDate()).isEqualTo(today.minusDays(1));
        assertThat(snapshot.dailyActiveUsers().get(5).userCount()).isEqualTo(1L);
        assertThat(snapshot.dailyActiveUsers().get(5).usernames()).containsExactly("bob");
        assertThat(snapshot.dailyActiveUsers().get(6).metricDate()).isEqualTo(today);
        assertThat(snapshot.dailyActiveUsers().get(6).userCount()).isEqualTo(1L);
        assertThat(snapshot.dailyActiveUsers().get(6).usernames()).containsExactly("alice");
        verify(adminDailyActiveUserRepository, never()).save(any(AdminDailyActiveUserEntity.class));
        verify(adminDailyActiveUserRepository, times(2)).deleteAllByMetricDateBefore(today.minusDays(6));
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
