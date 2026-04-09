package com.yoyuzh.files.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.api.v2.ApiV2ErrorCode;
import com.yoyuzh.api.v2.ApiV2Exception;
import com.yoyuzh.auth.User;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BackgroundTaskService {

    static final String STATE_PHASE_KEY = "phase";
    static final String STATE_ATTEMPT_COUNT_KEY = "attemptCount";
    static final String STATE_MAX_ATTEMPTS_KEY = "maxAttempts";
    static final String STATE_RETRY_SCHEDULED_KEY = "retryScheduled";
    static final String STATE_NEXT_RETRY_AT_KEY = "nextRetryAt";
    static final String STATE_RETRY_DELAY_SECONDS_KEY = "retryDelaySeconds";
    static final String STATE_LAST_FAILURE_MESSAGE_KEY = "lastFailureMessage";
    static final String STATE_LAST_FAILURE_AT_KEY = "lastFailureAt";
    static final String STATE_FAILURE_CATEGORY_KEY = "failureCategory";
    static final String STATE_WORKER_OWNER_KEY = "workerOwner";
    static final String STATE_HEARTBEAT_AT_KEY = "heartbeatAt";
    static final String STATE_LEASE_EXPIRES_AT_KEY = "leaseExpiresAt";
    static final String STATE_STARTED_AT_KEY = "startedAt";

    private static final List<String> ZIP_COMPATIBLE_EXTENSIONS = List.of(".zip", ".jar", ".war");
    private static final List<String> MEDIA_EXTENSIONS = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg",
            ".mp4", ".mov", ".mkv", ".webm", ".avi",
            ".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a"
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
    private static final int EXPIRED_RUNNING_TASK_BATCH_SIZE = 100;

    private final BackgroundTaskRepository backgroundTaskRepository;
    private final StoredFileRepository storedFileRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public BackgroundTask createQueuedFileTask(User user,
                                               BackgroundTaskType type,
                                               Long fileId,
                                               String requestedPath,
                                               String correlationId) {
        StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, user.getId())
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "file not found"));
        String logicalPath = buildLogicalPath(file);
        if (!logicalPath.equals(normalizeLogicalPath(requestedPath))) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "task path does not match file path");
        }
        validateTaskTarget(type, file);

        Map<String, Object> publicState = fileState(file, logicalPath);
        Map<String, Object> privateState = new LinkedHashMap<>(publicState);
        privateState.put("taskType", type.name());
        if (type == BackgroundTaskType.ARCHIVE) {
            String outputPath = file.getPath();
            String outputFilename = file.getFilename() + ".zip";
            publicState.put("outputPath", outputPath);
            publicState.put("outputFilename", outputFilename);
            privateState.put("outputPath", outputPath);
            privateState.put("outputFilename", outputFilename);
        } else if (type == BackgroundTaskType.EXTRACT) {
            String outputPath = file.getPath();
            String outputDirectoryName = deriveExtractOutputDirectoryName(file.getFilename());
            publicState.put("outputPath", outputPath);
            publicState.put("outputDirectoryName", outputDirectoryName);
            privateState.put("outputPath", outputPath);
            privateState.put("outputDirectoryName", outputDirectoryName);
        }
        return createQueuedTask(user, type, publicState, privateState, correlationId);
    }

    @Transactional
    public BackgroundTask createQueuedTask(User user,
                                           BackgroundTaskType type,
                                           Map<String, Object> publicState,
                                           Map<String, Object> privateState,
                                           String correlationId) {
        BackgroundTask task = new BackgroundTask();
        task.setUserId(user.getId());
        task.setType(type);
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setAttemptCount(0);
        task.setMaxAttempts(resolveMaxAttempts(type));
        task.setNextRunAt(null);
        Map<String, Object> nextPublicState = new LinkedHashMap<>(publicState == null ? Map.of() : publicState);
        nextPublicState.put(STATE_PHASE_KEY, "queued");
        nextPublicState.putAll(retryStatePatch(task.getAttemptCount(), task.getMaxAttempts()));
        task.setPublicStateJson(toJson(nextPublicState));
        task.setPrivateStateJson(toJson(privateState));
        task.setCorrelationId(normalizeCorrelationId(correlationId));
        return backgroundTaskRepository.save(task);
    }

    public Page<BackgroundTask> listOwnedTasks(User user, Pageable pageable) {
        return backgroundTaskRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
    }

    public BackgroundTask getOwnedTask(User user, Long id) {
        return backgroundTaskRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "task not found"));
    }

    @Transactional
    public BackgroundTask cancelOwnedTask(User user, Long id) {
        BackgroundTask task = getOwnedTask(user, id);
        if (task.isTerminal()) {
            return task;
        }

        if (task.getStatus() == BackgroundTaskStatus.QUEUED || task.getStatus() == BackgroundTaskStatus.RUNNING) {
            task.setStatus(BackgroundTaskStatus.CANCELLED);
            task.setNextRunAt(null);
            clearLease(task);
            task.setPublicStateJson(mergePublicStateJson(
                    task.getPublicStateJson(),
                    Map.of(
                            STATE_PHASE_KEY, "cancelled",
                            STATE_ATTEMPT_COUNT_KEY, task.getAttemptCount(),
                            STATE_MAX_ATTEMPTS_KEY, task.getMaxAttempts(),
                            STATE_HEARTBEAT_AT_KEY, LocalDateTime.now().toString()
                    ),
                    removableStateKeys(RETRY_TRANSIENT_STATE_KEYS, RUNNING_TRANSIENT_STATE_KEYS)
            ));
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(null);
            return backgroundTaskRepository.save(task);
        }

        return task;
    }

    @Transactional
    public BackgroundTask retryOwnedTask(User user, Long id) {
        BackgroundTask task = getOwnedTask(user, id);
        if (task.getStatus() != BackgroundTaskStatus.FAILED) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "only failed tasks can be retried");
        }

        task.setAttemptCount(0);
        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(resetPublicStateForRetry(task.getPrivateStateJson(), task.getAttemptCount(), task.getMaxAttempts()));
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markRunning(User user, Long id) {
        BackgroundTask task = getOwnedTask(user, id);
        if (task.isTerminal()) {
            return task;
        }
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setPublicStateJson(mergePublicStateJson(
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
    public BackgroundTask markCompleted(User user, Long id) {
        BackgroundTask task = getOwnedTask(user, id);
        if (task.isTerminal()) {
            return task;
        }
        task.setStatus(BackgroundTaskStatus.COMPLETED);
        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(mergePublicStateJson(
                task.getPublicStateJson(),
                Map.of(
                        STATE_PHASE_KEY, "completed",
                        STATE_ATTEMPT_COUNT_KEY, task.getAttemptCount(),
                        STATE_MAX_ATTEMPTS_KEY, task.getMaxAttempts(),
                        STATE_HEARTBEAT_AT_KEY, LocalDateTime.now().toString()
                ),
                removableStateKeys(RETRY_TRANSIENT_STATE_KEYS, RUNNING_TRANSIENT_STATE_KEYS)
        ));
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markFailed(User user, Long id, String errorMessage) {
        BackgroundTask task = getOwnedTask(user, id);
        if (task.isTerminal()) {
            return task;
        }
        task.setStatus(BackgroundTaskStatus.FAILED);
        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(mergePublicStateJson(
                task.getPublicStateJson(),
                Map.of(
                        STATE_PHASE_KEY, "failed",
                        STATE_ATTEMPT_COUNT_KEY, task.getAttemptCount(),
                        STATE_MAX_ATTEMPTS_KEY, task.getMaxAttempts(),
                        STATE_LAST_FAILURE_MESSAGE_KEY, StringUtils.hasText(errorMessage) ? errorMessage.trim() : "task failed",
                        STATE_LAST_FAILURE_AT_KEY, LocalDateTime.now().toString(),
                        STATE_FAILURE_CATEGORY_KEY, BackgroundTaskFailureCategory.UNKNOWN.name(),
                        STATE_HEARTBEAT_AT_KEY, LocalDateTime.now().toString()
                ),
                removableStateKeys(List.of(STATE_RETRY_SCHEDULED_KEY, STATE_NEXT_RETRY_AT_KEY), RUNNING_TRANSIENT_STATE_KEYS)
        ));
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(StringUtils.hasText(errorMessage) ? errorMessage.trim() : "task failed");
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public int requeueExpiredRunningTasks() {
        LocalDateTime now = LocalDateTime.now();
        int recovered = 0;
        for (Long taskId : backgroundTaskRepository.findExpiredRunningTaskIds(
                BackgroundTaskStatus.RUNNING,
                now,
                PageRequest.of(0, EXPIRED_RUNNING_TASK_BATCH_SIZE)
        )) {
            int requeued = backgroundTaskRepository.requeueExpiredRunningTask(
                    taskId,
                    BackgroundTaskStatus.RUNNING,
                    BackgroundTaskStatus.QUEUED,
                    now,
                    now
            );
            if (requeued != 1) {
                continue;
            }
            BackgroundTask task = backgroundTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "task not found"));
            resetTaskToQueued(task);
            backgroundTaskRepository.save(task);
            recovered += 1;
        }
        return recovered;
    }

    public List<Long> findQueuedTaskIds(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return backgroundTaskRepository.findReadyTaskIdsByStatusOrder(
                BackgroundTaskStatus.QUEUED,
                LocalDateTime.now(),
                PageRequest.of(0, limit)
        );
    }

    @Transactional
    public Optional<BackgroundTask> claimQueuedTask(Long id, String workerOwner, long leaseDurationSeconds) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiresAt = now.plusSeconds(Math.max(30L, leaseDurationSeconds));
        int claimed = backgroundTaskRepository.claimQueuedTask(
                id,
                BackgroundTaskStatus.QUEUED,
                BackgroundTaskStatus.RUNNING,
                workerOwner,
                leaseExpiresAt,
                now,
                now
        );
        if (claimed != 1) {
            return Optional.empty();
        }
        Optional<BackgroundTask> task = backgroundTaskRepository.findById(id);
        task.ifPresent(claimedTask -> {
            claimedTask.setLeaseOwner(workerOwner);
            claimedTask.setLeaseExpiresAt(leaseExpiresAt);
            claimedTask.setHeartbeatAt(now);
            claimedTask.setPublicStateJson(mergePublicStateJson(
                    claimedTask.getPublicStateJson(),
                    runningStatePatch(claimedTask, workerOwner, now, leaseExpiresAt, true),
                    RETRY_TRANSIENT_STATE_KEYS
            ));
        });
        task.ifPresent(backgroundTaskRepository::save);
        return task;
    }

    @Transactional
    public BackgroundTask markWorkerTaskProgress(Long id,
                                                 String workerOwner,
                                                 Map<String, Object> publicStatePatch,
                                                 long leaseDurationSeconds) {
        LeaseTouch leaseTouch = refreshLease(id, workerOwner, leaseDurationSeconds);
        BackgroundTask task = backgroundTaskRepository.findById(id)
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "task not found"));
        task.setLeaseOwner(workerOwner);
        task.setLeaseExpiresAt(leaseTouch.leaseExpiresAt());
        task.setHeartbeatAt(leaseTouch.now());
        Map<String, Object> nextPatch = new LinkedHashMap<>(runningStatePatch(
                task,
                workerOwner,
                leaseTouch.now(),
                leaseTouch.leaseExpiresAt(),
                false
        ));
        if (publicStatePatch != null) {
            nextPatch.putAll(publicStatePatch);
        }
        task.setPublicStateJson(mergePublicStateJson(task.getPublicStateJson(), nextPatch));
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markWorkerTaskCompleted(Long id,
                                                  String workerOwner,
                                                  Map<String, Object> publicStatePatch,
                                                  long leaseDurationSeconds) {
        LeaseTouch leaseTouch = refreshLease(id, workerOwner, leaseDurationSeconds);
        BackgroundTask task = backgroundTaskRepository.findById(id)
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "task not found"));
        Map<String, Object> nextPatch = new LinkedHashMap<>(publicStatePatch == null ? Map.of() : publicStatePatch);
        nextPatch.put(STATE_PHASE_KEY, "completed");
        nextPatch.put(STATE_ATTEMPT_COUNT_KEY, task.getAttemptCount());
        nextPatch.put(STATE_MAX_ATTEMPTS_KEY, task.getMaxAttempts());
        nextPatch.put(STATE_HEARTBEAT_AT_KEY, leaseTouch.now().toString());
        task.setPublicStateJson(mergePublicStateJson(
                task.getPublicStateJson(),
                nextPatch,
                removableStateKeys(RETRY_TRANSIENT_STATE_KEYS, RUNNING_TRANSIENT_STATE_KEYS)
        ));
        task.setStatus(BackgroundTaskStatus.COMPLETED);
        task.setNextRunAt(null);
        clearLease(task);
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markWorkerTaskFailed(Long id,
                                               String workerOwner,
                                               String errorMessage,
                                               BackgroundTaskFailureCategory failureCategory,
                                               long leaseDurationSeconds) {
        LeaseTouch leaseTouch = refreshLease(id, workerOwner, leaseDurationSeconds);
        BackgroundTask task = backgroundTaskRepository.findById(id)
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "task not found"));
        String normalizedErrorMessage = StringUtils.hasText(errorMessage) ? errorMessage.trim() : "task failed";
        LocalDateTime now = leaseTouch.now();
        if (failureCategory.isRetryable() && hasRemainingAttempts(task)) {
            long retryDelaySeconds = resolveRetryDelaySeconds(task.getType(), failureCategory, task.getAttemptCount());
            LocalDateTime nextRunAt = now.plusSeconds(retryDelaySeconds);
            task.setStatus(BackgroundTaskStatus.QUEUED);
            task.setNextRunAt(nextRunAt);
            clearLease(task);
            task.setFinishedAt(null);
            task.setErrorMessage(null);
            task.setPublicStateJson(mergePublicStateJson(
                    task.getPublicStateJson(),
                    Map.of(
                            STATE_PHASE_KEY, "queued",
                            STATE_ATTEMPT_COUNT_KEY, task.getAttemptCount(),
                            STATE_MAX_ATTEMPTS_KEY, task.getMaxAttempts(),
                            STATE_RETRY_SCHEDULED_KEY, true,
                            STATE_NEXT_RETRY_AT_KEY, nextRunAt.toString(),
                            STATE_RETRY_DELAY_SECONDS_KEY, retryDelaySeconds,
                            STATE_LAST_FAILURE_MESSAGE_KEY, normalizedErrorMessage,
                            STATE_LAST_FAILURE_AT_KEY, now.toString(),
                            STATE_FAILURE_CATEGORY_KEY, failureCategory.name(),
                            STATE_HEARTBEAT_AT_KEY, now.toString()
                    ),
                    RUNNING_TRANSIENT_STATE_KEYS
            ));
            return backgroundTaskRepository.save(task);
        }

        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(mergePublicStateJson(
                task.getPublicStateJson(),
                Map.of(
                        STATE_PHASE_KEY, "failed",
                        STATE_ATTEMPT_COUNT_KEY, task.getAttemptCount(),
                        STATE_MAX_ATTEMPTS_KEY, task.getMaxAttempts(),
                        STATE_LAST_FAILURE_MESSAGE_KEY, normalizedErrorMessage,
                        STATE_LAST_FAILURE_AT_KEY, now.toString(),
                        STATE_FAILURE_CATEGORY_KEY, failureCategory.name(),
                        STATE_HEARTBEAT_AT_KEY, now.toString()
                ),
                removableStateKeys(List.of(STATE_RETRY_SCHEDULED_KEY, STATE_NEXT_RETRY_AT_KEY, STATE_RETRY_DELAY_SECONDS_KEY), RUNNING_TRANSIENT_STATE_KEYS)
        ));
        task.setStatus(BackgroundTaskStatus.FAILED);
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

    private void validateTaskTarget(BackgroundTaskType type, StoredFile file) {
        if (type == BackgroundTaskType.ARCHIVE) {
            return;
        }
        if (file.isDirectory()) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "task target type is not supported");
        }
        if (type == BackgroundTaskType.EXTRACT && !isZipCompatibleArchive(file)) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "extract task only supports zip-compatible archives");
        }
        if (type == BackgroundTaskType.MEDIA_META && !isMediaLike(file)) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "media metadata task only supports media files");
        }
    }

    private Map<String, Object> fileState(StoredFile file, String logicalPath) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("fileId", file.getId());
        state.put("path", logicalPath);
        state.put("filename", file.getFilename());
        state.put("directory", file.isDirectory());
        state.put("contentType", file.getContentType());
        state.put("size", file.getSize());
        return state;
    }

    private boolean isZipCompatibleArchive(StoredFile file) {
        String contentType = normalizeContentType(file.getContentType());
        if (contentType.contains("zip") || contentType.contains("java-archive")) {
            return true;
        }
        return hasExtension(file.getFilename(), ZIP_COMPATIBLE_EXTENSIONS);
    }

    private boolean isMediaLike(StoredFile file) {
        String contentType = normalizeContentType(file.getContentType());
        if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")) {
            return true;
        }
        return hasExtension(file.getFilename(), MEDIA_EXTENSIONS);
    }

    private String deriveExtractOutputDirectoryName(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "extracted";
        }
        String trimmed = filename.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String extension : ZIP_COMPATIBLE_EXTENSIONS) {
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

    private String buildLogicalPath(StoredFile file) {
        String parent = normalizeLogicalPath(file.getPath());
        if ("/".equals(parent)) {
            return "/" + file.getFilename();
        }
        return parent + "/" + file.getFilename();
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

    private String toJson(Map<String, Object> value) {
        Map<String, Object> safeValue = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
        try {
            return objectMapper.writeValueAsString(safeValue);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize background task state", ex);
        }
    }

    private Map<String, Object> parseJsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashMap<>();
        }

        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse background task state", ex);
        }
    }

    private String mergePublicStateJson(String currentValue, Map<String, Object> patch) {
        return mergePublicStateJson(currentValue, patch, List.of());
    }

    private String mergePublicStateJson(String currentValue, Map<String, Object> patch, List<String> keysToRemove) {
        Map<String, Object> nextPublicState = parseJsonObject(currentValue);
        if (keysToRemove != null) {
            keysToRemove.forEach(nextPublicState::remove);
        }
        if (patch != null) {
            nextPublicState.putAll(patch);
        }
        return toJson(nextPublicState);
    }

    private String resetPublicStateForRetry(String privateStateJson, int attemptCount, int maxAttempts) {
        Map<String, Object> nextPublicState = parseJsonObject(privateStateJson);
        nextPublicState.remove("taskType");
        nextPublicState.put(STATE_PHASE_KEY, "queued");
        nextPublicState.putAll(retryStatePatch(attemptCount, maxAttempts));
        return toJson(nextPublicState);
    }

    private void resetTaskToQueued(BackgroundTask task) {
        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(resetPublicStateForRetry(task.getPrivateStateJson(), task.getAttemptCount(), task.getMaxAttempts()));
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setFinishedAt(null);
        task.setErrorMessage(null);
    }

    private int resolveMaxAttempts(BackgroundTaskType type) {
        return switch (type) {
            case ARCHIVE -> 4;
            case EXTRACT -> 3;
            case MEDIA_META -> 2;
            default -> 1;
        };
    }

    private Map<String, Object> retryStatePatch(int attemptCount, int maxAttempts) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(STATE_ATTEMPT_COUNT_KEY, attemptCount);
        patch.put(STATE_MAX_ATTEMPTS_KEY, maxAttempts);
        return patch;
    }

    private boolean hasRemainingAttempts(BackgroundTask task) {
        return task.getAttemptCount() != null
                && task.getMaxAttempts() != null
                && task.getAttemptCount() < task.getMaxAttempts();
    }

    private long resolveRetryDelaySeconds(BackgroundTaskType type,
                                          BackgroundTaskFailureCategory failureCategory,
                                          Integer attemptCount) {
        int safeAttemptCount = attemptCount == null ? 1 : Math.max(1, attemptCount);
        long baseDelaySeconds = switch (type) {
            case ARCHIVE -> 30L;
            case EXTRACT -> 45L;
            case MEDIA_META -> 15L;
            default -> 30L;
        };
        if (failureCategory == BackgroundTaskFailureCategory.RATE_LIMITED) {
            baseDelaySeconds *= 4L;
        } else if (failureCategory == BackgroundTaskFailureCategory.UNKNOWN) {
            baseDelaySeconds *= 2L;
        }
        long delay = baseDelaySeconds * (1L << Math.min(safeAttemptCount - 1, 2));
        return Math.min(delay, baseDelaySeconds * 4L);
    }

    private LeaseTouch refreshLease(Long id, String workerOwner, long leaseDurationSeconds) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiresAt = now.plusSeconds(Math.max(30L, leaseDurationSeconds));
        int refreshed = backgroundTaskRepository.refreshRunningTaskLease(
                id,
                BackgroundTaskStatus.RUNNING,
                workerOwner,
                leaseExpiresAt,
                now,
                now
        );
        if (refreshed != 1) {
            throw new BackgroundTaskLeaseLostException(id, workerOwner);
        }
        return new LeaseTouch(now, leaseExpiresAt);
    }

    private Map<String, Object> runningStatePatch(BackgroundTask task,
                                                  String workerOwner,
                                                  LocalDateTime heartbeatAt,
                                                  LocalDateTime leaseExpiresAt,
                                                  boolean includeStartedAt) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(STATE_PHASE_KEY, "running");
        patch.put(STATE_ATTEMPT_COUNT_KEY, task.getAttemptCount());
        patch.put(STATE_MAX_ATTEMPTS_KEY, task.getMaxAttempts());
        patch.put(STATE_WORKER_OWNER_KEY, workerOwner);
        patch.put(STATE_HEARTBEAT_AT_KEY, heartbeatAt.toString());
        patch.put(STATE_LEASE_EXPIRES_AT_KEY, leaseExpiresAt.toString());
        if (includeStartedAt) {
            patch.put(STATE_STARTED_AT_KEY, heartbeatAt.toString());
        }
        return patch;
    }

    private List<String> removableStateKeys(List<String> primary, List<String> secondary) {
        List<String> keys = new java.util.ArrayList<>(primary);
        keys.addAll(secondary);
        return keys;
    }

    private void clearLease(BackgroundTask task) {
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setHeartbeatAt(null);
    }

    private record LeaseTouch(LocalDateTime now, LocalDateTime leaseExpiresAt) {
    }
}
