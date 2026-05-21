package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.infra.BackgroundTaskRepository;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.TaskProgressResponse;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.files.workspace.api.WorkspaceFileQueryApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.infra.lock.DistributedLockGateway;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BackgroundTaskService {

    static final String STATE_PHASE_KEY = BackgroundTaskStateKeys.PHASE;
    static final String STATE_ATTEMPT_COUNT_KEY = BackgroundTaskStateKeys.ATTEMPT_COUNT;
    static final String STATE_MAX_ATTEMPTS_KEY = BackgroundTaskStateKeys.MAX_ATTEMPTS;
    static final String STATE_RETRY_SCHEDULED_KEY = BackgroundTaskStateKeys.RETRY_SCHEDULED;
    static final String STATE_NEXT_RETRY_AT_KEY = BackgroundTaskStateKeys.NEXT_RETRY_AT;
    static final String STATE_RETRY_DELAY_SECONDS_KEY = BackgroundTaskStateKeys.RETRY_DELAY_SECONDS;
    static final String STATE_LAST_FAILURE_MESSAGE_KEY = BackgroundTaskStateKeys.LAST_FAILURE_MESSAGE;
    static final String STATE_LAST_FAILURE_AT_KEY = BackgroundTaskStateKeys.LAST_FAILURE_AT;
    static final String STATE_FAILURE_CATEGORY_KEY = BackgroundTaskStateKeys.FAILURE_CATEGORY;
    static final String STATE_WORKER_OWNER_KEY = BackgroundTaskStateKeys.WORKER_OWNER;
    static final String STATE_HEARTBEAT_AT_KEY = BackgroundTaskStateKeys.HEARTBEAT_AT;
    static final String STATE_LEASE_EXPIRES_AT_KEY = BackgroundTaskStateKeys.LEASE_EXPIRES_AT;
    static final String STATE_STARTED_AT_KEY = BackgroundTaskStateKeys.STARTED_AT;

    private static final List<String> SUPPORTED_ARCHIVE_EXTENSIONS = List.of(
            ".tar.gz",
            ".tar.bz2",
            ".tar.xz",
            ".tgz",
            ".tbz2",
            ".tbz",
            ".txz",
            ".zip",
            ".jar",
            ".war",
            ".7z",
            ".rar",
            ".tar",
            ".gz",
            ".bz2",
            ".xz"
    );
    private static final List<String> RETRY_TRANSIENT_STATE_KEYS = List.of(
            STATE_RETRY_SCHEDULED_KEY,
            STATE_NEXT_RETRY_AT_KEY,
            STATE_RETRY_DELAY_SECONDS_KEY,
            STATE_LAST_FAILURE_MESSAGE_KEY,
            STATE_LAST_FAILURE_AT_KEY,
            STATE_FAILURE_CATEGORY_KEY
    );
    private static final List<String> RUNNING_TRANSIENT_STATE_KEYS = List.of(
            STATE_WORKER_OWNER_KEY,
            STATE_LEASE_EXPIRES_AT_KEY
    );
    private static final Duration CORRELATION_LOCK_TTL = Duration.ofSeconds(5);

    private final BackgroundTaskRepository backgroundTaskRepository;
    private final WorkspaceFileQueryApi workspaceFileQueryApi;
    private final DistributedLockGateway distributedLockGateway;
    private final BackgroundTaskRetryPolicy retryPolicy;
    private final BackgroundTaskStateManager stateManager;
    private final Clock clock;

    @Autowired
    public BackgroundTaskService(BackgroundTaskRepository backgroundTaskRepository,
                                 WorkspaceFileQueryApi workspaceFileQueryApi,
                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                 DistributedLockGateway distributedLockGateway,
                                 BackgroundTaskRetryPolicy retryPolicy,
                                 BackgroundTaskStateManager stateManager) {
        this(
                backgroundTaskRepository,
                workspaceFileQueryApi,
                objectMapper,
                distributedLockGateway,
                retryPolicy,
                stateManager,
                Clock.systemDefaultZone()
        );
    }

    BackgroundTaskService(BackgroundTaskRepository backgroundTaskRepository,
                          WorkspaceFileQueryApi workspaceFileQueryApi,
                          com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                          DistributedLockGateway distributedLockGateway,
                          BackgroundTaskRetryPolicy retryPolicy,
                          BackgroundTaskStateManager stateManager,
                          Clock clock) {
        this.backgroundTaskRepository = backgroundTaskRepository;
        this.workspaceFileQueryApi = workspaceFileQueryApi;
        this.distributedLockGateway = distributedLockGateway == null
                ? DistributedLockGateway.noOp()
                : distributedLockGateway;
        this.retryPolicy = retryPolicy == null ? new BackgroundTaskRetryPolicy() : retryPolicy;
        this.stateManager = stateManager == null ? new BackgroundTaskStateManager(objectMapper) : stateManager;
        this.clock = clock;
    }

    BackgroundTaskService(BackgroundTaskRepository backgroundTaskRepository,
                          WorkspaceFileQueryApi workspaceFileQueryApi,
                          com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                          DistributedLockGateway distributedLockGateway) {
        this(
                backgroundTaskRepository,
                workspaceFileQueryApi,
                objectMapper,
                distributedLockGateway,
                new BackgroundTaskRetryPolicy(),
                new BackgroundTaskStateManager(objectMapper)
        );
    }

    @Transactional
    public BackgroundTask createQueuedFileTask(Long userId,
                                               BackgroundTaskType type,
                                               Long fileId,
                                               String requestedPath,
                                               String correlationId) {
        WorkspaceFileSnapshot file = workspaceFileQueryApi.findOwnedActiveFile(userId, fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found"));
        String logicalPath = buildLogicalPath(file);
        if (!logicalPath.equals(normalizeLogicalPath(requestedPath))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "task path does not match file path");
        }
        return createQueuedFileTaskInternal(userId, type, file, correlationId, false);
    }

    @Transactional
    public Optional<BackgroundTask> createQueuedAutoMediaMetadataTask(Long userId,
                                                                      Long fileId,
                                                                      String correlationId) {
        String normalizedCorrelationId = StringUtils.hasText(correlationId)
                ? correlationId.trim()
                : "media-meta:auto:file:" + fileId;
        try {
            return distributedLockGateway.executeWithLock(
                    correlationLockName(normalizedCorrelationId),
                    CORRELATION_LOCK_TTL,
                    () -> {
                        if (backgroundTaskRepository.existsByCorrelationId(normalizedCorrelationId)) {
                            return Optional.empty();
                        }

                        return workspaceFileQueryApi.findOwnedActiveFile(userId, fileId)
                                .filter(file -> !file.directory())
                                .filter(file -> MediaTaskSupport.isMediaLike(file.filename(), file.contentType()))
                                .map(file -> createQueuedFileTaskInternal(
                                        userId,
                                        BackgroundTaskType.MEDIA_META,
                                        file,
                                        normalizedCorrelationId,
                                        true
                                ));
                    }
            );
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }
    }

    @Transactional
    public BackgroundTask createQueuedTaskByUserId(Long userId,
                                                   BackgroundTaskType type,
                                                   Map<String, Object> publicState,
                                                   Map<String, Object> privateState,
                                                   String correlationId) {
        return createQueuedTask(userId, type, publicState, privateState, correlationId);
    }

    private BackgroundTask createQueuedTask(Long userId,
                                            BackgroundTaskType type,
                                            Map<String, Object> publicState,
                                            Map<String, Object> privateState,
                                            String correlationId) {
        return createQueuedTask(userId, type, publicState, privateState, correlationId, false);
    }

    private BackgroundTask createQueuedTask(Long userId,
                                            BackgroundTaskType type,
                                            Map<String, Object> publicState,
                                            Map<String, Object> privateState,
                                            String correlationId,
                                            boolean flushOnSave) {
        Map<String, Object> safePublicState = publicState == null ? Map.of() : new LinkedHashMap<>(publicState);
        Map<String, Object> safePrivateState = privateState == null ? new LinkedHashMap<>() : new LinkedHashMap<>(privateState);
        safePrivateState.putIfAbsent("_publicStateSeed", new LinkedHashMap<>(safePublicState));
        BackgroundTask task = new BackgroundTask();
        task.setUserId(userId);
        task.setType(type);
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setAttemptCount(0);
        task.setMaxAttempts(retryPolicy.resolveMaxAttempts(type));
        task.setNextRunAt(null);
        task.setPublicStateJson(stateManager.createInitialPublicState(safePublicState, task.getAttemptCount(), task.getMaxAttempts()));
        task.setPrivateStateJson(stateManager.toJson(safePrivateState));
        task.setCorrelationId(normalizeCorrelationId(correlationId));
        return flushOnSave
                ? backgroundTaskRepository.saveAndFlush(task)
                : backgroundTaskRepository.save(task);
    }

    public Page<BackgroundTask> listOwnedTasks(Long userId, Pageable pageable) {
        return backgroundTaskRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public BackgroundTask getOwnedTask(Long userId, Long id) {
        return backgroundTaskRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "task not found"));
    }

    @Transactional(readOnly = true)
    public Optional<BackgroundTask> findTaskById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return backgroundTaskRepository.findById(id);
    }

    public TaskProgressResponse getOwnedTaskProgress(Long userId, Long id) {
        BackgroundTask task = getOwnedTask(userId, id);
        Map<String, Object> state = stateManager.parseJsonObject(task.getPublicStateJson(), "Failed to parse background task state");
        long processedItems = sumProgressItems(
                stateManager.readLong(state.get("processedItems")),
                stateManager.readLong(state.get("processedFileCount")),
                stateManager.readLong(state.get("processedDirectoryCount"))
        );
        long totalItems = sumProgressItems(
                stateManager.readLong(state.get("totalItems")),
                stateManager.readLong(state.get("totalFileCount")),
                stateManager.readLong(state.get("totalDirectoryCount"))
        );
        Long explicitPercent = stateManager.readLong(state.get("progressPercent"));
        int progressPercent = explicitPercent == null
                ? deriveProgressPercent(processedItems, totalItems)
                : (int) Math.max(0L, Math.min(100L, explicitPercent));
        String message = firstNonBlank(
                stateManager.readText(state.get("message")),
                task.getErrorMessage(),
                task.getStatus().name()
        );
        return new TaskProgressResponse(task.getId(), task.getStatus().name(), progressPercent, processedItems, totalItems, message);
    }

    @Transactional
    public BackgroundTask createSearchIndexRebuildTask(Long requestedByUserId) {
        Map<String, Object> publicState = new LinkedHashMap<>();
        publicState.put("message", "search index rebuild queued");
        publicState.put("processedItems", 0);
        publicState.put("totalItems", 1);
        publicState.put("progressPercent", 0);

        Map<String, Object> privateState = new LinkedHashMap<>(publicState);
        privateState.put("taskType", BackgroundTaskType.SEARCH_INDEX_REBUILD.name());
        privateState.put("requestedByUserId", requestedByUserId);

        return createQueuedTask(
                requestedByUserId,
                BackgroundTaskType.SEARCH_INDEX_REBUILD,
                publicState,
                privateState,
                "search-index-rebuild:" + UUID.randomUUID().toString().replace("-", "")
        );
    }

    @Transactional
    public BackgroundTask cancelOwnedTask(Long userId, Long id) {
        BackgroundTask task = getOwnedTask(userId, id);
        if (task.isTerminal()) {
            return task;
        }

        if (task.getStatus() == BackgroundTaskStatus.QUEUED || task.getStatus() == BackgroundTaskStatus.RUNNING) {
            LocalDateTime now = now();
            String publicStateJson = stateManager.merge(
                    task.getPublicStateJson(),
                    stateManager.cancelledStatePatch(task, now),
                    stateManager.removableKeys(RETRY_TRANSIENT_STATE_KEYS, RUNNING_TRANSIENT_STATE_KEYS)
            );
            int updated = backgroundTaskRepository.cancelOwnedTask(
                    id,
                    userId,
                    task.getUpdatedAt(),
                    List.of(BackgroundTaskStatus.QUEUED, BackgroundTaskStatus.RUNNING),
                    BackgroundTaskStatus.CANCELLED,
                    publicStateJson,
                    now,
                    now
            );
            return getOwnedTask(userId, id);
        }

        return task;
    }

    @Transactional
    public BackgroundTask retryOwnedTask(Long userId, Long id) {
        BackgroundTask task = getOwnedTask(userId, id);
        if (task.getStatus() != BackgroundTaskStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "only failed tasks can be retried");
        }

        String publicStateJson = stateManager.resetPublicStateForRetry(
                task.getPublicStateJson(),
                task.getPrivateStateJson(),
                0,
                task.getMaxAttempts()
        );
        LocalDateTime now = now();
        int updated = backgroundTaskRepository.retryOwnedTask(
                id,
                userId,
                task.getUpdatedAt(),
                BackgroundTaskStatus.FAILED,
                BackgroundTaskStatus.QUEUED,
                publicStateJson,
                now
        );
        if (updated == 1) {
            return getOwnedTask(userId, id);
        }
        BackgroundTask current = getOwnedTask(userId, id);
        if (current.getStatus() != BackgroundTaskStatus.FAILED) {
            return current;
        }
        throw new IllegalStateException("background task retry transition conflict");
    }

    @Transactional
    public BackgroundTask markRunning(Long userId, Long id) {
        BackgroundTask task = getOwnedTask(userId, id);
        if (task.isTerminal()) {
            return task;
        }
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setPublicStateJson(stateManager.merge(
                task.getPublicStateJson(),
                Map.of(
                        STATE_PHASE_KEY, "running",
                        STATE_ATTEMPT_COUNT_KEY, task.getAttemptCount(),
                        STATE_MAX_ATTEMPTS_KEY, task.getMaxAttempts()
                ),
                List.of(STATE_RETRY_SCHEDULED_KEY, STATE_NEXT_RETRY_AT_KEY)
        ));
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markCompleted(Long userId, Long id) {
        BackgroundTask task = getOwnedTask(userId, id);
        if (task.isTerminal()) {
            return task;
        }
        LocalDateTime now = now();
        task.setStatus(BackgroundTaskStatus.COMPLETED);
        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(stateManager.merge(
                task.getPublicStateJson(),
                stateManager.completedStatePatch(task, now, null),
                stateManager.removableKeys(RETRY_TRANSIENT_STATE_KEYS, RUNNING_TRANSIENT_STATE_KEYS)
        ));
        task.setFinishedAt(now);
        task.setErrorMessage(null);
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markFailed(Long userId, Long id, String errorMessage) {
        BackgroundTask task = getOwnedTask(userId, id);
        if (task.isTerminal()) {
            return task;
        }
        LocalDateTime now = now();
        task.setStatus(BackgroundTaskStatus.FAILED);
        task.setNextRunAt(null);
        clearLease(task);
        String normalizedErrorMessage = StringUtils.hasText(errorMessage) ? errorMessage.trim() : "task failed";
        task.setPublicStateJson(stateManager.merge(
                task.getPublicStateJson(),
                stateManager.failedStatePatch(
                        task,
                        normalizedErrorMessage,
                        BackgroundTaskFailureCategory.UNKNOWN,
                        now
                ),
                stateManager.removableKeys(List.of(STATE_RETRY_SCHEDULED_KEY, STATE_NEXT_RETRY_AT_KEY), RUNNING_TRANSIENT_STATE_KEYS)
        ));
        task.setFinishedAt(now);
        task.setErrorMessage(normalizedErrorMessage);
        return backgroundTaskRepository.save(task);
    }

    private String normalizeCorrelationId(String correlationId) {
        if (StringUtils.hasText(correlationId)) {
            return correlationId.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String correlationLockName(String correlationId) {
        return "background-task-correlation:" + correlationId;
    }

    private void validateTaskTarget(BackgroundTaskType type, WorkspaceFileSnapshot file) {
        if (type == BackgroundTaskType.ARCHIVE) {
            return;
        }
        if (file.directory()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "task target type is not supported");
        }
        if (type == BackgroundTaskType.EXTRACT && !isSupportedArchive(file)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "extract task only supports supported archive files");
        }
        if (type == BackgroundTaskType.MEDIA_META
                && !MediaTaskSupport.isMediaLike(file.filename(), file.contentType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "media metadata task only supports media files");
        }
    }

    private Map<String, Object> fileState(WorkspaceFileSnapshot file, String logicalPath) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("fileId", file.id());
        state.put("path", logicalPath);
        state.put("filename", file.filename());
        state.put("directory", file.directory());
        state.put("contentType", file.contentType());
        state.put("size", file.size());
        return state;
    }

    private boolean isSupportedArchive(WorkspaceFileSnapshot file) {
        String contentType = normalizeContentType(file.contentType());
        if (contentType.contains("zip")
                || contentType.contains("java-archive")
                || contentType.contains("7z")
                || contentType.contains("rar")
                || contentType.contains("tar")
                || contentType.contains("gzip")
                || contentType.contains("bzip2")
                || contentType.contains("xz")) {
            return true;
        }
        return hasExtension(file.filename(), SUPPORTED_ARCHIVE_EXTENSIONS);
    }

    private String deriveExtractOutputDirectoryName(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "extracted";
        }
        String trimmed = filename.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String extension : SUPPORTED_ARCHIVE_EXTENSIONS) {
            if (lower.endsWith(extension) && trimmed.length() > extension.length()) {
                return trimmed.substring(0, trimmed.length() - extension.length());
            }
        }
        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot > 0) {
            return trimmed.substring(0, lastDot);
        }
        return trimmed;
    }

    private boolean hasExtension(String filename, List<String> extensions) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(normalized::endsWith);
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private BackgroundTask createQueuedFileTaskInternal(Long userId,
                                                        BackgroundTaskType type,
                                                        WorkspaceFileSnapshot file,
                                                        String correlationId) {
        return createQueuedFileTaskInternal(userId, type, file, correlationId, false);
    }

    private BackgroundTask createQueuedFileTaskInternal(Long userId,
                                                        BackgroundTaskType type,
                                                        WorkspaceFileSnapshot file,
                                                        String correlationId,
                                                        boolean flushOnSave) {
        String logicalPath = buildLogicalPath(file);
        validateTaskTarget(type, file);

        Map<String, Object> publicState = fileState(file, logicalPath);
        Map<String, Object> privateState = new LinkedHashMap<>(publicState);
        privateState.put("taskType", type.name());
        if (type == BackgroundTaskType.ARCHIVE) {
            String outputPath = file.path();
            String outputFilename = file.filename() + ".zip";
            publicState.put("outputPath", outputPath);
            publicState.put("outputFilename", outputFilename);
            privateState.put("outputPath", outputPath);
            privateState.put("outputFilename", outputFilename);
        } else if (type == BackgroundTaskType.EXTRACT) {
            String outputPath = file.path();
            String outputDirectoryName = deriveExtractOutputDirectoryName(file.filename());
            publicState.put("outputPath", outputPath);
            publicState.put("outputDirectoryName", outputDirectoryName);
            privateState.put("outputPath", outputPath);
            privateState.put("outputDirectoryName", outputDirectoryName);
        }
        return createQueuedTask(userId, type, publicState, privateState, correlationId, flushOnSave);
    }

    private String buildLogicalPath(WorkspaceFileSnapshot file) {
        String parent = normalizeLogicalPath(file.path());
        if ("/".equals(parent)) {
            return "/" + file.filename();
        }
        return parent + "/" + file.filename();
    }

    private String normalizeLogicalPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void clearLease(BackgroundTask task) {
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setHeartbeatAt(null);
    }

    private long sumProgressItems(Long aggregate, Long fileCount, Long directoryCount) {
        if (aggregate != null) {
            return Math.max(0L, aggregate);
        }
        return Math.max(0L, fileCount == null ? 0L : fileCount)
                + Math.max(0L, directoryCount == null ? 0L : directoryCount);
    }

    private int deriveProgressPercent(long processedItems, long totalItems) {
        if (totalItems <= 0L) {
            return 0;
        }
        return (int) Math.min(100L, Math.max(0L, processedItems) * 100L / totalItems);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
