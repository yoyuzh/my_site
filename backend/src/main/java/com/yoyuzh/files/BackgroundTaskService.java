package com.yoyuzh.files;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.api.v2.ApiV2ErrorCode;
import com.yoyuzh.api.v2.ApiV2Exception;
import com.yoyuzh.auth.User;
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

    private static final List<String> ARCHIVE_EXTENSIONS = List.of(
            ".zip", ".jar", ".war", ".7z", ".rar", ".tar", ".gz", ".tgz", ".bz2", ".xz"
    );
    private static final List<String> MEDIA_EXTENSIONS = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg",
            ".mp4", ".mov", ".mkv", ".webm", ".avi",
            ".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a"
    );

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
        task.setPublicStateJson(toJson(publicState));
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
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(null);
            return backgroundTaskRepository.save(task);
        }

        return task;
    }

    @Transactional
    public BackgroundTask markRunning(User user, Long id) {
        BackgroundTask task = getOwnedTask(user, id);
        if (task.isTerminal()) {
            return task;
        }
        task.setStatus(BackgroundTaskStatus.RUNNING);
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markCompleted(User user, Long id) {
        BackgroundTask task = getOwnedTask(user, id);
        if (task.isTerminal()) {
            return task;
        }
        task.setStatus(BackgroundTaskStatus.COMPLETED);
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
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(StringUtils.hasText(errorMessage) ? errorMessage.trim() : "task failed");
        return backgroundTaskRepository.save(task);
    }

    public List<Long> findQueuedTaskIds(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return backgroundTaskRepository.findByStatusOrderByCreatedAtAsc(
                        BackgroundTaskStatus.QUEUED,
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(BackgroundTask::getId)
                .toList();
    }

    @Transactional
    public Optional<BackgroundTask> claimQueuedTask(Long id) {
        int claimed = backgroundTaskRepository.claimQueuedTask(
                id,
                BackgroundTaskStatus.QUEUED,
                BackgroundTaskStatus.RUNNING,
                LocalDateTime.now()
        );
        if (claimed != 1) {
            return Optional.empty();
        }
        return backgroundTaskRepository.findById(id);
    }

    @Transactional
    public BackgroundTask markWorkerTaskCompleted(Long id, Map<String, Object> publicStatePatch) {
        BackgroundTask task = backgroundTaskRepository.findById(id)
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "task not found"));
        if (task.isTerminal() || task.getStatus() != BackgroundTaskStatus.RUNNING) {
            return task;
        }

        Map<String, Object> nextPublicState = parseJsonObject(task.getPublicStateJson());
        if (publicStatePatch != null) {
            nextPublicState.putAll(publicStatePatch);
        }
        task.setPublicStateJson(toJson(nextPublicState));
        task.setStatus(BackgroundTaskStatus.COMPLETED);
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markWorkerTaskFailed(Long id, String errorMessage) {
        BackgroundTask task = backgroundTaskRepository.findById(id)
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "task not found"));
        if (task.isTerminal()) {
            return task;
        }

        task.setStatus(BackgroundTaskStatus.FAILED);
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(StringUtils.hasText(errorMessage) ? errorMessage.trim() : "task failed");
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
        if (type == BackgroundTaskType.EXTRACT && !isArchiveLike(file)) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "extract task only supports archive files");
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

    private boolean isArchiveLike(StoredFile file) {
        String contentType = normalizeContentType(file.getContentType());
        if (contentType.contains("zip")
                || contentType.contains("x-tar")
                || contentType.contains("gzip")
                || contentType.contains("x-7z")
                || contentType.contains("x-rar")
                || contentType.contains("java-archive")) {
            return true;
        }
        return hasExtension(file.getFilename(), ARCHIVE_EXTENSIONS);
    }

    private boolean isMediaLike(StoredFile file) {
        String contentType = normalizeContentType(file.getContentType());
        if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")) {
            return true;
        }
        return hasExtension(file.getFilename(), MEDIA_EXTENSIONS);
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
}
