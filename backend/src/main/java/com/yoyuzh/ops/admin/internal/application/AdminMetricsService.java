package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.ops.admin.api.AdminOfflineTransferStorageLimitResponse;
import com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminMetricsState;
import com.yoyuzh.ops.admin.internal.infra.AdminMetricsStateRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private static final Long STATE_ID = 1L;
    private static final long DEFAULT_OFFLINE_TRANSFER_STORAGE_LIMIT_BYTES = 20L * 1024 * 1024 * 1024;
    private static final int DAILY_ACTIVE_USER_RETENTION_DAYS = 7;

    private final AdminMetricsStateRepository adminMetricsStateRepository;
    private final AdminRequestTimelinePointRepository adminRequestTimelinePointRepository;
    private final AdminDailyActiveUserRepository adminDailyActiveUserRepository;

    @Transactional
    public AdminMetricsSnapshot getSnapshot() {
        LocalDate today = LocalDate.now();
        pruneExpiredDailyActiveUsers(today);
        AdminMetricsState state = refreshRequestCountDateIfNeeded(ensureCurrentState(), today, true);
        return toSnapshot(state, today);
    }

    @Transactional
    public long getOfflineTransferStorageLimitBytes() {
        return ensureCurrentState().getOfflineTransferStorageLimitBytes();
    }

    @Transactional
    public void recordUserOnline(Long userId, String username) {
        if (userId == null || username == null || username.isBlank()) {
            return;
        }
        LocalDate today = LocalDate.now();
        pruneExpiredDailyActiveUsers(today);
        AdminDailyActiveUserEntity entry = adminDailyActiveUserRepository.findByMetricDateAndUserIdForUpdate(today, userId)
                .orElseGet(() -> createDailyActiveUser(today, userId, username));
        if (!username.equals(entry.getUsername())) {
            entry.setUsername(username);
            adminDailyActiveUserRepository.save(entry);
        }
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
                buildDailyActiveUsers(metricDate),
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
        int currentHour = LocalDate.now().equals(metricDate) ? LocalDateTime.now().getHour() : 23;
        return IntStream.rangeClosed(0, currentHour)
                .mapToObj(hour -> new AdminRequestTimelinePoint(hour, formatHourLabel(hour), countsByHour.getOrDefault(hour, 0L)))
                .toList();
    }

    private List<AdminDailyActiveUserSummary> buildDailyActiveUsers(LocalDate today) {
        LocalDate startDate = today.minusDays(DAILY_ACTIVE_USER_RETENTION_DAYS - 1L);
        Map<LocalDate, java.util.List<String>> usernamesByDate = new TreeMap<>();
        for (AdminDailyActiveUserEntity entry : adminDailyActiveUserRepository
                .findAllByMetricDateBetweenOrderByMetricDateAscUsernameAsc(startDate, today)) {
            usernamesByDate.computeIfAbsent(entry.getMetricDate(), ignored -> new java.util.ArrayList<>())
                    .add(entry.getUsername());
        }
        return IntStream.range(0, DAILY_ACTIVE_USER_RETENTION_DAYS)
                .mapToObj(offset -> startDate.plusDays(offset))
                .map(metricDate -> {
                    List<String> usernames = List.copyOf(usernamesByDate.getOrDefault(metricDate, List.of()));
                    return new AdminDailyActiveUserSummary(
                            metricDate,
                            formatDailyActiveUserLabel(metricDate, today),
                            usernames.size(),
                            usernames
                    );
                })
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

    private AdminDailyActiveUserEntity createDailyActiveUser(LocalDate metricDate, Long userId, String username) {
        AdminDailyActiveUserEntity entry = new AdminDailyActiveUserEntity();
        entry.setMetricDate(metricDate);
        entry.setUserId(userId);
        entry.setUsername(username);
        try {
            return adminDailyActiveUserRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ignored) {
            return adminDailyActiveUserRepository.findByMetricDateAndUserIdForUpdate(metricDate, userId)
                    .orElseThrow(() -> ignored);
        }
    }

    private void pruneExpiredDailyActiveUsers(LocalDate today) {
        adminDailyActiveUserRepository.deleteAllByMetricDateBefore(today.minusDays(DAILY_ACTIVE_USER_RETENTION_DAYS - 1L));
    }

    private String formatHourLabel(int hour) {
        return "%02d:00".formatted(hour);
    }

    private String formatDailyActiveUserLabel(LocalDate metricDate, LocalDate today) {
        if (metricDate.equals(today)) {
            return "今天";
        }
        if (metricDate.equals(today.minusDays(1))) {
            return "昨天";
        }
        return "%02d-%02d".formatted(metricDate.getMonthValue(), metricDate.getDayOfMonth());
    }
}
