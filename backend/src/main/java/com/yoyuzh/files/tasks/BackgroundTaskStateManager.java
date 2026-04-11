package com.yoyuzh.files.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BackgroundTaskStateManager {

    private static final TypeReference<LinkedHashMap<String, Object>> JSON_OBJECT_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public BackgroundTaskStateManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Map<String, Object> value) {
        Map<String, Object> safeValue = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
        try {
            return objectMapper.writeValueAsString(safeValue);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize background task state", ex);
        }
    }

    public Map<String, Object> retryStatePatch(int attemptCount, int maxAttempts) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(BackgroundTaskStateKeys.ATTEMPT_COUNT, attemptCount);
        patch.put(BackgroundTaskStateKeys.MAX_ATTEMPTS, maxAttempts);
        return patch;
    }

    public Map<String, Object> runningStatePatch(BackgroundTask task,
                                                 String workerOwner,
                                                 LocalDateTime heartbeatAt,
                                                 LocalDateTime leaseExpiresAt,
                                                 boolean includeStartedAt) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(BackgroundTaskStateKeys.PHASE, "running");
        patch.put(BackgroundTaskStateKeys.ATTEMPT_COUNT, task.getAttemptCount());
        patch.put(BackgroundTaskStateKeys.MAX_ATTEMPTS, task.getMaxAttempts());
        patch.put(BackgroundTaskStateKeys.WORKER_OWNER, workerOwner);
        patch.put(BackgroundTaskStateKeys.HEARTBEAT_AT, heartbeatAt.toString());
        patch.put(BackgroundTaskStateKeys.LEASE_EXPIRES_AT, leaseExpiresAt.toString());
        if (includeStartedAt) {
            patch.put(BackgroundTaskStateKeys.STARTED_AT, heartbeatAt.toString());
        }
        return patch;
    }

    public Map<String, Object> cancelledStatePatch(BackgroundTask task, LocalDateTime heartbeatAt) {
        return terminalStatePatch("cancelled", task, heartbeatAt);
    }

    public Map<String, Object> completedStatePatch(BackgroundTask task,
                                                   LocalDateTime heartbeatAt,
                                                   Map<String, Object> additionalPatch) {
        Map<String, Object> patch = new LinkedHashMap<>(additionalPatch == null ? Map.of() : additionalPatch);
        patch.putAll(terminalStatePatch("completed", task, heartbeatAt));
        return patch;
    }

    public Map<String, Object> failedStatePatch(BackgroundTask task,
                                                String errorMessage,
                                                BackgroundTaskFailureCategory failureCategory,
                                                LocalDateTime heartbeatAt) {
        Map<String, Object> patch = new LinkedHashMap<>(terminalStatePatch("failed", task, heartbeatAt));
        patch.put(BackgroundTaskStateKeys.LAST_FAILURE_MESSAGE, errorMessage);
        patch.put(BackgroundTaskStateKeys.LAST_FAILURE_AT, heartbeatAt.toString());
        patch.put(BackgroundTaskStateKeys.FAILURE_CATEGORY, failureCategory.name());
        return patch;
    }

    public Map<String, Object> retryQueuedStatePatch(BackgroundTask task,
                                                     String errorMessage,
                                                     BackgroundTaskFailureCategory failureCategory,
                                                     LocalDateTime nextRetryAt,
                                                     long retryDelaySeconds,
                                                     LocalDateTime heartbeatAt) {
        Map<String, Object> patch = new LinkedHashMap<>(retryStatePatch(task.getAttemptCount(), task.getMaxAttempts()));
        patch.put(BackgroundTaskStateKeys.PHASE, "queued");
        patch.put(BackgroundTaskStateKeys.RETRY_SCHEDULED, true);
        patch.put(BackgroundTaskStateKeys.NEXT_RETRY_AT, nextRetryAt.toString());
        patch.put(BackgroundTaskStateKeys.RETRY_DELAY_SECONDS, retryDelaySeconds);
        patch.put(BackgroundTaskStateKeys.LAST_FAILURE_MESSAGE, errorMessage);
        patch.put(BackgroundTaskStateKeys.LAST_FAILURE_AT, heartbeatAt.toString());
        patch.put(BackgroundTaskStateKeys.FAILURE_CATEGORY, failureCategory.name());
        patch.put(BackgroundTaskStateKeys.HEARTBEAT_AT, heartbeatAt.toString());
        return patch;
    }

    public String createInitialPublicState(Map<String, Object> baseState, int attemptCount, int maxAttempts) {
        Map<String, Object> nextPublicState = new LinkedHashMap<>(baseState == null ? Map.of() : baseState);
        nextPublicState.put(BackgroundTaskStateKeys.PHASE, "queued");
        nextPublicState.putAll(retryStatePatch(attemptCount, maxAttempts));
        return toJson(nextPublicState);
    }

    public String merge(String currentValue, Map<String, Object> patch) {
        return merge(currentValue, patch, List.of());
    }

    public String merge(String currentValue, Map<String, Object> patch, List<String> keysToRemove) {
        Map<String, Object> nextPublicState = parse(currentValue);
        if (keysToRemove != null) {
            keysToRemove.forEach(nextPublicState::remove);
        }
        if (patch != null) {
            nextPublicState.putAll(patch);
        }
        return toJson(nextPublicState);
    }

    public String resetPublicStateForRetry(String privateStateJson, int attemptCount, int maxAttempts) {
        Map<String, Object> nextPublicState = parse(privateStateJson);
        nextPublicState.remove("taskType");
        nextPublicState.put(BackgroundTaskStateKeys.PHASE, "queued");
        nextPublicState.putAll(retryStatePatch(attemptCount, maxAttempts));
        return toJson(nextPublicState);
    }

    public List<String> removableKeys(List<String> primary, List<String> secondary) {
        List<String> keys = new java.util.ArrayList<>(primary);
        keys.addAll(secondary);
        return keys;
    }

    public Map<String, Object> parseJsonObject(String value, String invalidStateMessage) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, JSON_OBJECT_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(invalidStateMessage, ex);
        }
    }

    public Map<String, Object> mergeJsonObjects(String primaryJson,
                                                String overlayJson,
                                                String invalidStateMessage) {
        Map<String, Object> state = new LinkedHashMap<>(parseJsonObject(primaryJson, invalidStateMessage));
        state.putAll(parseJsonObject(overlayJson, invalidStateMessage));
        return state;
    }

    public Long readLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    public String readText(Object value) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    private Map<String, Object> terminalStatePatch(String phase,
                                                   BackgroundTask task,
                                                   LocalDateTime heartbeatAt) {
        Map<String, Object> patch = new LinkedHashMap<>(retryStatePatch(task.getAttemptCount(), task.getMaxAttempts()));
        patch.put(BackgroundTaskStateKeys.PHASE, phase);
        patch.put(BackgroundTaskStateKeys.HEARTBEAT_AT, heartbeatAt.toString());
        return patch;
    }

    private Map<String, Object> parse(String value) {
        return new LinkedHashMap<>(parseJsonObject(value, "Failed to parse background task state"));
    }
}
