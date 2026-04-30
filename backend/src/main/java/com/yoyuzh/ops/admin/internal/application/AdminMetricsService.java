package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceDownloadMetricsPort;
import com.yoyuzh.files.workspace.api.WorkspaceAdminMetricsApi;
import com.yoyuzh.files.sharing.api.SharingAdminMetricsApi;
import com.yoyuzh.ops.admin.api.AdminOfflineTransferStorageLimitResponse;
import com.yoyuzh.ops.admin.api.AdminRequestMetricsApi;
import com.yoyuzh.platform.job.api.BackgroundTaskAdminQueryApi;
import com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminMetricsState;
import com.yoyuzh.ops.admin.internal.infra.AdminMetricsStateRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.TreeMap;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class AdminMetricsService implements WorkspaceDownloadMetricsPort, AdminRequestMetricsApi {

    private static final Long STATE_ID = 1L;
    private static final long DEFAULT_OFFLINE_TRANSFER_STORAGE_LIMIT_BYTES = 20L * 1024 * 1024 * 1024;
    private static final int DAILY_ACTIVE_USER_RETENTION_DAYS = 7;
    private final Object stateInitializationMonitor = new Object();

    private final AdminMetricsStateRepository adminMetricsStateRepository;
    private final AdminRequestTimelinePointRepository adminRequestTimelinePointRepository;
    private final AdminDailyActiveUserRepository adminDailyActiveUserRepository;
    private final WorkspaceAdminMetricsApi workspaceAdminMetricsApi;
    private final SharingAdminMetricsApi sharingAdminMetricsApi;
    private final BackgroundTaskAdminQueryApi backgroundTaskAdminQueryApi;

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
    @Override
    public void recordUserOnline(Long userId, String username) {
        if (userId == null || username == null || username.isBlank()) {
            return;
        }
        LocalDate today = LocalDate.now();
        pruneExpiredDailyActiveUsers(today);
        AdminDailyActiveUserEntity entry = adminDailyActiveUserRepository.findByMetricDateAndUserId(today, userId)
                .orElseGet(() -> createDailyActiveUser(today, userId, username));
        if (!username.equals(entry.getUsername())) {
            entry.setUsername(username);
            adminDailyActiveUserRepository.save(entry);
        }
    }

    @Transactional
    @Override
    public void incrementRequestCount() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        ensureStateUpdated(today, now, () -> adminMetricsStateRepository.incrementRequestCount(STATE_ID, today, now));
        incrementRequestTimelinePoint(today, now.getHour(), now);
    }

    @Transactional
    @Override
    public void recordDownloadTraffic(long bytes) {
        if (bytes <= 0) {
            return;
        }
        ensureStateUpdated(LocalDate.now(), LocalDateTime.now(), () -> adminMetricsStateRepository.incrementDownloadTrafficBytes(
                STATE_ID,
                bytes,
                LocalDateTime.now()
        ));
    }

    @Transactional
    public void recordTransferUsage(long bytes) {
        if (bytes <= 0) {
            return;
        }
        ensureStateUpdated(LocalDate.now(), LocalDateTime.now(), () -> adminMetricsStateRepository.incrementTransferUsageBytes(
                STATE_ID,
                bytes,
                LocalDateTime.now()
        ));
    }

    @Transactional
    public void initializeState() {
        LocalDateTime now = LocalDateTime.now();
        synchronized (stateInitializationMonitor) {
            adminMetricsStateRepository.insertIfAbsent(
                    STATE_ID,
                    0L,
                    now.toLocalDate(),
                    0L,
                    0L,
                    DEFAULT_OFFLINE_TRANSFER_STORAGE_LIMIT_BYTES,
                    now
            );
        }
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
                workspaceAdminMetricsApi.countFavoriteFilesAsAdmin(),
                sharingAdminMetricsApi.totalDownloadCountAsAdmin(),
                backgroundTaskAdminQueryApi.countActiveTasks(),
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
                            .orElseThrow(() -> new IllegalStateException("admin metrics state initialization failed"));
                });
    }

    private AdminMetricsState createInitialState() {
        initializeState();
        return adminMetricsStateRepository.findById(STATE_ID)
                .orElseThrow(() -> new IllegalStateException("admin metrics state initialization failed"));
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

    private void incrementRequestTimelinePoint(LocalDate metricDate, int hour, LocalDateTime updatedAt) {
        if (adminRequestTimelinePointRepository.incrementRequestCount(metricDate, hour, updatedAt) > 0) {
            return;
        }
        ensureCurrentStateForUpdate();
        if (adminRequestTimelinePointRepository.incrementRequestCount(metricDate, hour, updatedAt) > 0) {
            return;
        }
        AdminRequestTimelinePointEntity point = new AdminRequestTimelinePointEntity();
        point.setMetricDate(metricDate);
        point.setHour(hour);
        point.setRequestCount(1L);
        adminRequestTimelinePointRepository.save(point);
    }

    private AdminDailyActiveUserEntity createDailyActiveUser(LocalDate metricDate, Long userId, String username) {
        ensureCurrentStateForUpdate();
        return adminDailyActiveUserRepository.findByMetricDateAndUserIdForUpdate(metricDate, userId)
                .orElseGet(() -> {
                    AdminDailyActiveUserEntity entry = new AdminDailyActiveUserEntity();
                    entry.setMetricDate(metricDate);
                    entry.setUserId(userId);
                    entry.setUsername(username);
                    return adminDailyActiveUserRepository.save(entry);
                });
    }

    private void pruneExpiredDailyActiveUsers(LocalDate today) {
        adminDailyActiveUserRepository.deleteAllByMetricDateBefore(today.minusDays(DAILY_ACTIVE_USER_RETENTION_DAYS - 1L));
    }

    private void ensureStateUpdated(LocalDate requestCountDate, LocalDateTime updatedAt, Supplier<Integer> updateOperation) {
        if (updateOperation.get() > 0) {
            return;
        }
        initializeStateIfMissing(requestCountDate, updatedAt);
        if (updateOperation.get() == 0) {
            throw new IllegalStateException("admin metrics state update failed");
        }
    }

    private void initializeStateIfMissing(LocalDate requestCountDate, LocalDateTime updatedAt) {
        synchronized (stateInitializationMonitor) {
            adminMetricsStateRepository.insertIfAbsent(
                    STATE_ID,
                    0L,
                    requestCountDate,
                    0L,
                    0L,
                    DEFAULT_OFFLINE_TRANSFER_STORAGE_LIMIT_BYTES,
                    updatedAt
            );
        }
    }

    private String formatHourLabel(int hour) {
        return "%02d:00".formatted(hour);
    }

    private String formatDailyActiveUserLabel(LocalDate metricDate, LocalDate today) {
        if (metricDate.equals(today)) {
            return "today";
        }
        if (metricDate.equals(today.minusDays(1))) {
            return "yesterday";
        }
        return "%02d-%02d".formatted(metricDate.getMonthValue(), metricDate.getDayOfMonth());
    }
}
