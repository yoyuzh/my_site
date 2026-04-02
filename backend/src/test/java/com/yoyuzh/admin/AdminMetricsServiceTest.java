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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceTest {

    @Mock
    private AdminMetricsStateRepository adminMetricsStateRepository;
    @Mock
    private AdminRequestTimelinePointRepository adminRequestTimelinePointRepository;

    private AdminMetricsService adminMetricsService;

    @BeforeEach
    void setUp() {
        adminMetricsService = new AdminMetricsService(adminMetricsStateRepository, adminRequestTimelinePointRepository);
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

        AdminMetricsSnapshot snapshot = adminMetricsService.getSnapshot();

        assertThat(snapshot.requestCount()).isZero();
        assertThat(state.getRequestCount()).isZero();
        assertThat(state.getRequestCountDate()).isEqualTo(LocalDate.now());
        assertThat(snapshot.requestTimeline()).hasSize(24);
        assertThat(snapshot.requestTimeline().get(0)).isEqualTo(new AdminRequestTimelinePoint(0, "00:00", 0L));
        verify(adminMetricsStateRepository).save(state);
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
}
