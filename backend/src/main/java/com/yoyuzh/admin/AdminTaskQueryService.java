package com.yoyuzh.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskFailureCategory;
import com.yoyuzh.files.tasks.BackgroundTaskRepository;
import com.yoyuzh.files.tasks.BackgroundTaskStatus;
import com.yoyuzh.files.tasks.BackgroundTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminTaskQueryService {

    private final BackgroundTaskRepository backgroundTaskRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PageResponse<AdminTaskResponse> listTasks(int page,
                                                     int size,
                                                     String userQuery,
                                                     BackgroundTaskType type,
                                                     BackgroundTaskStatus status,
                                                     BackgroundTaskFailureCategory failureCategory,
                                                     AdminTaskLeaseState leaseState) {
        String failureCategoryPattern = failureCategory == null
                ? null
                : "\"failureCategory\":\"" + failureCategory.name() + "\"";
        Page<BackgroundTask> result = backgroundTaskRepository.searchAdminTasks(
                normalizeQuery(userQuery),
                type,
                status,
                failureCategoryPattern,
                leaseState == null ? null : leaseState.name(),
                LocalDateTime.now(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Map<Long, User> ownerById = userRepository.findAllById(result.getContent().stream()
                        .map(BackgroundTask::getUserId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        return new PageResponse<>(
                result.getContent().stream()
                        .map(task -> toAdminTaskResponse(task, ownerById.get(task.getUserId())))
                        .toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    public AdminTaskResponse getTask(Long taskId) {
        BackgroundTask task = backgroundTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "task not found"));
        User owner = userRepository.findById(task.getUserId()).orElse(null);
        return toAdminTaskResponse(task, owner);
    }

    private AdminTaskResponse toAdminTaskResponse(BackgroundTask task, User owner) {
        Map<String, Object> state = parseState(task.getPublicStateJson());
        return new AdminTaskResponse(
                task.getId(),
                task.getType(),
                task.getStatus(),
                task.getUserId(),
                owner == null ? null : owner.getUsername(),
                owner == null ? null : owner.getEmail(),
                task.getPublicStateJson(),
                task.getCorrelationId(),
                task.getErrorMessage(),
                task.getAttemptCount(),
                task.getMaxAttempts(),
                task.getNextRunAt(),
                task.getLeaseOwner(),
                task.getLeaseExpiresAt(),
                task.getHeartbeatAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFinishedAt(),
                readStringState(state, "failureCategory"),
                readBooleanState(state, "retryScheduled"),
                readStringState(state, "workerOwner"),
                resolveLeaseState(task)
        );
    }

    private Map<String, Object> parseState(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String readStringState(Map<String, Object> state, String key) {
        Object value = state.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Boolean readBooleanState(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return null;
    }

    private AdminTaskLeaseState resolveLeaseState(BackgroundTask task) {
        if (!StringUtils.hasText(task.getLeaseOwner()) || task.getLeaseExpiresAt() == null) {
            return AdminTaskLeaseState.NONE;
        }
        return task.getLeaseExpiresAt().isBefore(LocalDateTime.now())
                ? AdminTaskLeaseState.EXPIRED
                : AdminTaskLeaseState.ACTIVE;
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }
}
