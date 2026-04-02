package com.yoyuzh.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private static final Long STATE_ID = 1L;
    private static final long DEFAULT_OFFLINE_TRANSFER_STORAGE_LIMIT_BYTES = 20L * 1024 * 1024 * 1024;

    private final AdminMetricsStateRepository adminMetricsStateRepository;
    private final AdminRequestTimelinePointRepository adminRequestTimelinePointRepository;

    @Transactional
    public AdminMetricsSnapshot getSnapshot() {
        LocalDate today = LocalDate.now();
        AdminMetricsState state = refreshRequestCountDateIfNeeded(ensureCurrentState(), today, true);
        return toSnapshot(state, today);
    }

    @Transactional
    public long getOfflineTransferStorageLimitBytes() {
        return ensureCurrentState().getOfflineTransferStorageLimitBytes();
    }

    @Transactional
    public void incrementRequestCount() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        AdminMetricsState state = refreshRequestCountDateIfNeeded(ensureCurrentStateForUpdate(), today, false);
        state.setRequestCount(state.getRequestCount() + 1);
        adminMetricsStateRepository.save(state);
        incrementRequestTimelinePoint(today, now.getHour());
    }

    @Transactional
    public void recordDownloadTraffic(long bytes) {
        if (bytes <= 0) {
            return;
        }
        AdminMetricsState state = ensureCurrentStateForUpdate();
        state.setDownloadTrafficBytes(state.getDownloadTrafficBytes() + bytes);
        adminMetricsStateRepository.save(state);
    }

    @Transactional
    public void recordTransferUsage(long bytes) {
        if (bytes <= 0) {
            return;
        }
        AdminMetricsState state = ensureCurrentStateForUpdate();
        state.setTransferUsageBytes(state.getTransferUsageBytes() + bytes);
        adminMetricsStateRepository.save(state);
    }

    @Transactional
    public AdminOfflineTransferStorageLimitResponse updateOfflineTransferStorageLimit(long offlineTransferStorageLimitBytes) {
        AdminMetricsState state = ensureCurrentStateForUpdate();
        state.setOfflineTransferStorageLimitBytes(offlineTransferStorageLimitBytes);
        AdminMetricsState saved = adminMetricsStateRepository.save(state);
        return new AdminOfflineTransferStorageLimitResponse(saved.getOfflineTransferStorageLimitBytes());
    }

    private AdminMetricsSnapshot toSnapshot(AdminMetricsState state, LocalDate metricDate) {
        return new AdminMetricsSnapshot(
                state.getRequestCount(),
                state.getDownloadTrafficBytes(),
                state.getTransferUsageBytes(),
                state.getOfflineTransferStorageLimitBytes(),
                buildRequestTimeline(metricDate)
        );
    }

    private AdminMetricsState ensureCurrentState() {
        return adminMetricsStateRepository.findById(STATE_ID)
                .orElseGet(this::createInitialState);
    }

    private AdminMetricsState ensureCurrentStateForUpdate() {
        return adminMetricsStateRepository.findByIdForUpdate(STATE_ID)
                .orElseGet(() -> {
                    createInitialState();
                    return adminMetricsStateRepository.findByIdForUpdate(STATE_ID)
                            .orElseThrow(() -> new IllegalStateException("管理统计状态初始化失败"));
                });
    }

    private AdminMetricsState createInitialState() {
        AdminMetricsState state = new AdminMetricsState();
        state.setId(STATE_ID);
        state.setRequestCount(0L);
        state.setRequestCountDate(LocalDate.now());
        state.setDownloadTrafficBytes(0L);
        state.setTransferUsageBytes(0L);
        state.setOfflineTransferStorageLimitBytes(DEFAULT_OFFLINE_TRANSFER_STORAGE_LIMIT_BYTES);
        try {
            return adminMetricsStateRepository.saveAndFlush(state);
        } catch (DataIntegrityViolationException ignored) {
            return adminMetricsStateRepository.findById(STATE_ID)
                    .orElseThrow(() -> ignored);
        }
    }

    private AdminMetricsState refreshRequestCountDateIfNeeded(AdminMetricsState state, LocalDate today, boolean persistImmediately) {
        if (today.equals(state.getRequestCountDate())) {
            return state;
        }
        state.setRequestCount(0L);
        state.setRequestCountDate(today);
        if (persistImmediately) {
            return adminMetricsStateRepository.save(state);
        }
        return state;
    }

    private List<AdminRequestTimelinePoint> buildRequestTimeline(LocalDate metricDate) {
        Map<Integer, Long> countsByHour = new HashMap<>();
        for (AdminRequestTimelinePointEntity point : adminRequestTimelinePointRepository.findAllByMetricDateOrderByHourAsc(metricDate)) {
            countsByHour.put(point.getHour(), point.getRequestCount());
        }
        return IntStream.range(0, 24)
                .mapToObj(hour -> new AdminRequestTimelinePoint(hour, formatHourLabel(hour), countsByHour.getOrDefault(hour, 0L)))
                .toList();
    }

    private void incrementRequestTimelinePoint(LocalDate metricDate, int hour) {
        AdminRequestTimelinePointEntity point = adminRequestTimelinePointRepository
                .findByMetricDateAndHourForUpdate(metricDate, hour)
                .orElseGet(() -> createTimelinePoint(metricDate, hour));
        point.setRequestCount(point.getRequestCount() + 1);
        adminRequestTimelinePointRepository.save(point);
    }

    private AdminRequestTimelinePointEntity createTimelinePoint(LocalDate metricDate, int hour) {
        AdminRequestTimelinePointEntity point = new AdminRequestTimelinePointEntity();
        point.setMetricDate(metricDate);
        point.setHour(hour);
        point.setRequestCount(0L);
        try {
            return adminRequestTimelinePointRepository.saveAndFlush(point);
        } catch (DataIntegrityViolationException ignored) {
            return adminRequestTimelinePointRepository.findByMetricDateAndHourForUpdate(metricDate, hour)
                    .orElseThrow(() -> ignored);
        }
    }

    private String formatHourLabel(int hour) {
        return "%02d:00".formatted(hour);
    }
}
