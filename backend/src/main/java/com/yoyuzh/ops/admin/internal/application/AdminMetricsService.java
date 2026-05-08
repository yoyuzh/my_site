package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.files.sharing.api.SharingAdminMetricsApi;
import com.yoyuzh.files.workspace.api.WorkspaceAdminMetricsApi;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadMetricsPort;
import com.yoyuzh.ops.admin.api.AdminOfflineTransferStorageLimitResponse;
import com.yoyuzh.ops.admin.api.AdminRequestMetricsApi;
import com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminMetricsState;
import com.yoyuzh.ops.admin.internal.infra.AdminMetricsStateRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointRepository;
import com.yoyuzh.platform.job.api.BackgroundTaskAdminQueryApi;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@Service
public class AdminMetricsService implements WorkspaceDownloadMetricsPort, AdminRequestMetricsApi {

    private static final Logger log = LoggerFactory.getLogger(AdminMetricsService.class);
    private static final Long STATE_ID = 1L;
    private static final long DEFAULT_OFFLINE_TRANSFER_STORAGE_LIMIT_BYTES = 20L * 1024 * 1024 * 1024;
    private static final int DAILY_ACTIVE_USER_RETENTION_DAYS = 7;

    private final Object stateInitializationMonitor = new Object();
    private final AtomicLong pendingRequestCount = new AtomicLong();
    private final AtomicLong pendingDownloadTrafficBytes = new AtomicLong();
    private final AtomicLong pendingTransferUsageBytes = new AtomicLong();
    private final ConcurrentMap<DailyActiveUserKey, String> pendingDailyActiveUsers = new ConcurrentHashMap<>();
    private final ConcurrentMap<DailyActiveUserKey, Boolean> knownDailyActiveUsers = new ConcurrentHashMap<>();
    private final AdminMetricsStateRepository adminMetricsStateRepository;
    private final AdminRequestTimelinePointRepository adminRequestTimelinePointRepository;
    private final AdminDailyActiveUserRepository adminDailyActiveUserRepository;
    private final WorkspaceAdminMetricsApi workspaceAdminMetricsApi;
    private final SharingAdminMetricsApi sharingAdminMetricsApi;
    private final BackgroundTaskAdminQueryApi backgroundTaskAdminQueryApi;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public AdminMetricsService(AdminMetricsStateRepository adminMetricsStateRepository,
                               AdminRequestTimelinePointRepository adminRequestTimelinePointRepository,
                               AdminDailyActiveUserRepository adminDailyActiveUserRepository,
                               WorkspaceAdminMetricsApi workspaceAdminMetricsApi,
                               SharingAdminMetricsApi sharingAdminMetricsApi,
                               BackgroundTaskAdminQueryApi backgroundTaskAdminQueryApi,
                               EntityManager entityManager,
                               ObjectProvider<Clock> clockProvider) {
        this(
                adminMetricsStateRepository,
                adminRequestTimelinePointRepository,
                adminDailyActiveUserRepository,
                workspaceAdminMetricsApi,
                sharingAdminMetricsApi,
                backgroundTaskAdminQueryApi,
                entityManager,
                clockProvider.getIfAvailable(Clock::systemDefaultZone)
        );
    }

    AdminMetricsService(AdminMetricsStateRepository adminMetricsStateRepository,
                        AdminRequestTimelinePointRepository adminRequestTimelinePointRepository,
                        AdminDailyActiveUserRepository adminDailyActiveUserRepository,
                        WorkspaceAdminMetricsApi workspaceAdminMetricsApi,
                        SharingAdminMetricsApi sharingAdminMetricsApi,
                        BackgroundTaskAdminQueryApi backgroundTaskAdminQueryApi,
                        EntityManager entityManager) {
        this(
                adminMetricsStateRepository,
                adminRequestTimelinePointRepository,
                adminDailyActiveUserRepository,
                workspaceAdminMetricsApi,
                sharingAdminMetricsApi,
                backgroundTaskAdminQueryApi,
                entityManager,
                Clock.systemDefaultZone()
        );
    }

    AdminMetricsService(AdminMetricsStateRepository adminMetricsStateRepository,
                        AdminRequestTimelinePointRepository adminRequestTimelinePointRepository,
                        AdminDailyActiveUserRepository adminDailyActiveUserRepository,
                        WorkspaceAdminMetricsApi workspaceAdminMetricsApi,
                        SharingAdminMetricsApi sharingAdminMetricsApi,
                        BackgroundTaskAdminQueryApi backgroundTaskAdminQueryApi,
                        EntityManager entityManager,
                        Clock clock) {
        this.adminMetricsStateRepository = adminMetricsStateRepository;
        this.adminRequestTimelinePointRepository = adminRequestTimelinePointRepository;
        this.adminDailyActiveUserRepository = adminDailyActiveUserRepository;
        this.workspaceAdminMetricsApi = workspaceAdminMetricsApi;
        this.sharingAdminMetricsApi = sharingAdminMetricsApi;
        this.backgroundTaskAdminQueryApi = backgroundTaskAdminQueryApi;
        this.entityManager = entityManager;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    @Transactional
    public AdminMetricsSnapshot getSnapshot() {
        LocalDateTime now = LocalDateTime.now(clock);
        flushPendingMetrics(now);
        // Bulk metric updates can leave a managed AdminMetricsState stale inside the same transaction.
        entityManager.clear();
        LocalDate today = now.toLocalDate();
        AdminMetricsState state = refreshRequestCountDateIfNeeded(ensureCurrentState(), today, true);
        return toSnapshot(state, today);
    }

    @Transactional
    public long getOfflineTransferStorageLimitBytes() {
        return ensureCurrentState().getOfflineTransferStorageLimitBytes();
    }

    @Override
    public void recordUserOnline(Long userId, String username) {
        if (userId == null || username == null || username.isBlank()) {
            return;
        }
        DailyActiveUserKey key = new DailyActiveUserKey(LocalDate.now(clock), userId);
        if (knownDailyActiveUsers.putIfAbsent(key, Boolean.TRUE) == null) {
            pendingDailyActiveUsers.put(key, username.trim());
        }
    }

    @Override
    public void incrementRequestCount() {
        pendingRequestCount.incrementAndGet();
    }

    @Override
    public void recordDownloadTraffic(long bytes) {
        if (bytes <= 0L) {
            return;
        }
        pendingDownloadTrafficBytes.addAndGet(bytes);
    }

    public void recordTransferUsage(long bytes) {
        if (bytes <= 0L) {
            return;
        }
        pendingTransferUsageBytes.addAndGet(bytes);
    }

    @Transactional
    public void initializeState() {
        LocalDateTime now = LocalDateTime.now(clock);
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

    @Scheduled(
            fixedDelayString = "${app.admin.metrics.flush-fixed-delay-ms:5000}",
            initialDelayString = "${app.admin.metrics.flush-initial-delay-ms:5000}"
    )
    @Transactional
    public void flushPendingMetrics() {
        flushPendingMetrics(LocalDateTime.now(clock));
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
        LocalDate today = LocalDate.now(clock);
        int currentHour = today.equals(metricDate) ? LocalDateTime.now(clock).getHour() : 23;
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

    private void flushPendingMetrics(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        pruneExpiredDailyActiveUsers(today);
        flushRequestCount(now, today);
        flushDownloadTraffic(now);
        flushTransferUsage(now);
        flushDailyActiveUsers();
    }

    private void flushRequestCount(LocalDateTime now, LocalDate metricDate) {
        long delta = pendingRequestCount.getAndSet(0L);
        if (delta <= 0L) {
            return;
        }
        try {
            ensureStateUpdated(metricDate, now, () -> adminMetricsStateRepository.incrementRequestCountBy(STATE_ID, metricDate, delta, now));
            incrementRequestTimelinePoint(metricDate, now.getHour(), delta, now);
        } catch (RuntimeException ex) {
            pendingRequestCount.addAndGet(delta);
            throw ex;
        }
    }

    private void flushDownloadTraffic(LocalDateTime now) {
        long delta = pendingDownloadTrafficBytes.getAndSet(0L);
        if (delta <= 0L) {
            return;
        }
        try {
            ensureStateUpdated(now.toLocalDate(), now, () -> adminMetricsStateRepository.incrementDownloadTrafficBytes(
                    STATE_ID,
                    delta,
                    now
            ));
        } catch (RuntimeException ex) {
            pendingDownloadTrafficBytes.addAndGet(delta);
            throw ex;
        }
    }

    private void flushTransferUsage(LocalDateTime now) {
        long delta = pendingTransferUsageBytes.getAndSet(0L);
        if (delta <= 0L) {
            return;
        }
        try {
            ensureStateUpdated(now.toLocalDate(), now, () -> adminMetricsStateRepository.incrementTransferUsageBytes(
                    STATE_ID,
                    delta,
                    now
            ));
        } catch (RuntimeException ex) {
            pendingTransferUsageBytes.addAndGet(delta);
            throw ex;
        }
    }

    private void flushDailyActiveUsers() {
        if (pendingDailyActiveUsers.isEmpty()) {
            return;
        }
        List<Map.Entry<DailyActiveUserKey, String>> snapshot = new ArrayList<>(pendingDailyActiveUsers.entrySet());
        for (Map.Entry<DailyActiveUserKey, String> entry : snapshot) {
            if (!pendingDailyActiveUsers.remove(entry.getKey(), entry.getValue())) {
                continue;
            }
            try {
                adminDailyActiveUserRepository.insertIfAbsent(
                        entry.getKey().metricDate(),
                        entry.getKey().userId(),
                        entry.getValue()
                );
            } catch (RuntimeException ex) {
                pendingDailyActiveUsers.put(entry.getKey(), entry.getValue());
                log.warn(
                        "failed to flush daily active user metric for date={} userId={}",
                        entry.getKey().metricDate(),
                        entry.getKey().userId(),
                        ex
                );
            }
        }
    }

    private void incrementRequestTimelinePoint(LocalDate metricDate, int hour, long delta, LocalDateTime updatedAt) {
        if (adminRequestTimelinePointRepository.incrementRequestCount(metricDate, hour, delta, updatedAt) > 0) {
            return;
        }
        ensureCurrentStateForUpdate();
        if (adminRequestTimelinePointRepository.incrementRequestCount(metricDate, hour, delta, updatedAt) > 0) {
            return;
        }
        AdminRequestTimelinePointEntity point = new AdminRequestTimelinePointEntity();
        point.setMetricDate(metricDate);
        point.setHour(hour);
        point.setRequestCount(delta);
        adminRequestTimelinePointRepository.save(point);
    }

    private void pruneExpiredDailyActiveUsers(LocalDate today) {
        synchronized (stateInitializationMonitor) {
            LocalDate cutoffDate = today.minusDays(DAILY_ACTIVE_USER_RETENTION_DAYS - 1L);
            adminDailyActiveUserRepository.deleteAllByMetricDateBefore(cutoffDate);
            knownDailyActiveUsers.keySet().removeIf(key -> key.metricDate().isBefore(cutoffDate));
        }
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

    private record DailyActiveUserKey(LocalDate metricDate, Long userId) {
    }
}
