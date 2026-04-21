package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.infra.BackgroundTaskRepository;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.boot.web.v2.ApiV2ErrorCode;
import com.yoyuzh.boot.web.v2.ApiV2Exception;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.infra.lock.DistributedLockGateway;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    private static final List<String> ZIP_COMPATIBLE_EXTENSIONS = List.of(".zip", ".jar", ".war");
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
    private final StoredFileRepository storedFileRepository;
    private final DistributedLockGateway distributedLockGateway;
    private final BackgroundTaskRetryPolicy retryPolicy;
    private final BackgroundTaskStateManager stateManager;

    @Autowired
    public BackgroundTaskService(BackgroundTaskRepository backgroundTaskRepository,
                                 StoredFileRepository storedFileRepository,
                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                 DistributedLockGateway distributedLockGateway,
                                 BackgroundTaskRetryPolicy retryPolicy,
                                 BackgroundTaskStateManager stateManager) {
        this.backgroundTaskRepository = backgroundTaskRepository;
        this.storedFileRepository = storedFileRepository;
        this.distributedLockGateway = distributedLockGateway == null
                ? DistributedLockGateway.noOp()
                : distributedLockGateway;
        this.retryPolicy = retryPolicy == null ? new BackgroundTaskRetryPolicy() : retryPolicy;
        this.stateManager = stateManager == null ? new BackgroundTaskStateManager(objectMapper) : stateManager;
    }

    BackgroundTaskService(BackgroundTaskRepository backgroundTaskRepository,
                          StoredFileRepository storedFileRepository,
                          com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                          DistributedLockGateway distributedLockGateway) {
        this(
                backgroundTaskRepository,
                storedFileRepository,
                objectMapper,
                distributedLockGateway,
                new BackgroundTaskRetryPolicy(),
                new BackgroundTaskStateManager(objectMapper)
        );
    }

    @Transactional
    public BackgroundTask createQueuedFileTask(User user,
                                               BackgroundTaskType type,
                                               Long fileId,
                                               String requestedPath,
                                               String correlationId) {
        return createQueuedFileTask(user.getId(), type, fileId, requestedPath, correlationId);
    }

    @Transactional
    public BackgroundTask createQueuedFileTask(Long userId,
                                               BackgroundTaskType type,
                                               Long fileId,
                                               String requestedPath,
                                               String correlationId) {
        StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, userId)
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "file not found"));
        String logicalPath = buildLogicalPath(file);
        if (!logicalPath.equals(normalizeLogicalPath(requestedPath))) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "task path does not match file path");
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

                        return storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, userId)
                                .filter(file -> !file.isDirectory())
                                .filter(file -> MediaTaskSupport.isMediaLike(file.getFilename(), file.getContentType()))
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
    public BackgroundTask createQueuedTask(User user,
                                           BackgroundTaskType type,
                                           Map<String, Object> publicState,
                                           Map<String, Object> privateState,
                                           String correlationId) {
        return createQueuedTaskByUserId(user.getId(), type, publicState, privateState, correlationId);
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
        BackgroundTask task = new BackgroundTask();
        task.setUserId(userId);
        task.setType(type);
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setAttemptCount(0);
        task.setMaxAttempts(retryPolicy.resolveMaxAttempts(type));
        task.setNextRunAt(null);
        task.setPublicStateJson(stateManager.createInitialPublicState(publicState, task.getAttemptCount(), task.getMaxAttempts()));
        task.setPrivateStateJson(stateManager.toJson(privateState));
        task.setCorrelationId(normalizeCorrelationId(correlationId));
        return flushOnSave
                ? backgroundTaskRepository.saveAndFlush(task)
                : backgroundTaskRepository.save(task);
    }

    public Page<BackgroundTask> listOwnedTasks(User user, Pageable pageable) {
        return listOwnedTasks(user.getId(), pageable);
    }

    public Page<BackgroundTask> listOwnedTasks(Long userId, Pageable pageable) {
        return backgroundTaskRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public BackgroundTask getOwnedTask(User user, Long id) {
        return getOwnedTask(user.getId(), id);
    }

    public BackgroundTask getOwnedTask(Long userId, Long id) {
        return backgroundTaskRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "task not found"));
    }

    @Transactional
    public BackgroundTask cancelOwnedTask(User user, Long id) {
        return cancelOwnedTask(user.getId(), id);
    }

    @Transactional
    public BackgroundTask cancelOwnedTask(Long userId, Long id) {
        BackgroundTask task = getOwnedTask(userId, id);
        if (task.isTerminal()) {
            return task;
        }

        if (task.getStatus() == BackgroundTaskStatus.QUEUED || task.getStatus() == BackgroundTaskStatus.RUNNING) {
            task.setStatus(BackgroundTaskStatus.CANCELLED);
            task.setNextRunAt(null);
            clearLease(task);
            task.setPublicStateJson(stateManager.merge(
                    task.getPublicStateJson(),
                    stateManager.cancelledStatePatch(task, LocalDateTime.now()),
                    stateManager.removableKeys(RETRY_TRANSIENT_STATE_KEYS, RUNNING_TRANSIENT_STATE_KEYS)
            ));
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(null);
            return backgroundTaskRepository.save(task);
        }

        return task;
    }

    @Transactional
    public BackgroundTask retryOwnedTask(User user, Long id) {
        return retryOwnedTask(user.getId(), id);
    }

    @Transactional
    public BackgroundTask retryOwnedTask(Long userId, Long id) {
        BackgroundTask task = getOwnedTask(userId, id);
        if (task.getStatus() != BackgroundTaskStatus.FAILED) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "only failed tasks can be retried");
        }

        task.setAttemptCount(0);
        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(stateManager.resetPublicStateForRetry(task.getPrivateStateJson(), task.getAttemptCount(), task.getMaxAttempts()));
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
    public BackgroundTask markCompleted(User user, Long id) {
        BackgroundTask task = getOwnedTask(user, id);
        if (task.isTerminal()) {
            return task;
        }
        task.setStatus(BackgroundTaskStatus.COMPLETED);
        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(stateManager.merge(
                task.getPublicStateJson(),
                stateManager.completedStatePatch(task, LocalDateTime.now(), null),
                stateManager.removableKeys(RETRY_TRANSIENT_STATE_KEYS, RUNNING_TRANSIENT_STATE_KEYS)
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
        String normalizedErrorMessage = StringUtils.hasText(errorMessage) ? errorMessage.trim() : "task failed";
        task.setPublicStateJson(stateManager.merge(
                task.getPublicStateJson(),
                stateManager.failedStatePatch(
                        task,
                        normalizedErrorMessage,
                        BackgroundTaskFailureCategory.UNKNOWN,
                        LocalDateTime.now()
                ),
                stateManager.removableKeys(List.of(STATE_RETRY_SCHEDULED_KEY, STATE_NEXT_RETRY_AT_KEY), RUNNING_TRANSIENT_STATE_KEYS)
        ));
        task.setFinishedAt(LocalDateTime.now());
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
        if (type == BackgroundTaskType.MEDIA_META
                && !MediaTaskSupport.isMediaLike(file.getFilename(), file.getContentType())) {
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

    private BackgroundTask createQueuedFileTaskInternal(Long userId,
                                                        BackgroundTaskType type,
                                                        StoredFile file,
                                                        String correlationId) {
        return createQueuedFileTaskInternal(userId, type, file, correlationId, false);
    }

    private BackgroundTask createQueuedFileTaskInternal(Long userId,
                                                        BackgroundTaskType type,
                                                        StoredFile file,
                                                        String correlationId,
                                                        boolean flushOnSave) {
        String logicalPath = buildLogicalPath(file);
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
        return createQueuedTask(userId, type, publicState, privateState, correlationId, flushOnSave);
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

    private void clearLease(BackgroundTask task) {
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setHeartbeatAt(null);
    }
}
