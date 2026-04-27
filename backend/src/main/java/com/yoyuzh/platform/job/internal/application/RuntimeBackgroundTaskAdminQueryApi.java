package com.yoyuzh.platform.job.internal.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.platform.job.internal.infra.BackgroundTaskRepository;
import com.yoyuzh.platform.job.api.AdminBackgroundTaskQuery;
import com.yoyuzh.platform.job.api.AdminBackgroundTaskView;
import com.yoyuzh.platform.job.api.BackgroundTaskAdminQueryApi;
import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskLeaseState;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RuntimeBackgroundTaskAdminQueryApi implements BackgroundTaskAdminQueryApi {

    private final BackgroundTaskRepository backgroundTaskRepository;
    private final ObjectMapper objectMapper;

    @Override
    public PageResponse<AdminBackgroundTaskView> listTasks(AdminBackgroundTaskQuery query) {
        String failureCategoryPattern = query.failureCategory() == null
                ? null
                : "\"failureCategory\":\"" + query.failureCategory().name() + "\"";
        Page<BackgroundTask> result = backgroundTaskRepository.searchAdminTasks(
                normalizeQuery(query.userQuery()),
                query.type(),
                query.status(),
                failureCategoryPattern,
                query.leaseState() == null ? null : query.leaseState().name(),
                LocalDateTime.now(),
                PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toAdminView).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Override
    public AdminBackgroundTaskView getTask(Long taskId) {
        BackgroundTask task = backgroundTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "task not found"));
        return toAdminView(task);
    }

    @Override
    public long countActiveTasks() {
        return backgroundTaskRepository.countByStatuses(List.of(
                com.yoyuzh.platform.job.api.BackgroundTaskStatus.QUEUED,
                com.yoyuzh.platform.job.api.BackgroundTaskStatus.RUNNING
        ));
    }

    private AdminBackgroundTaskView toAdminView(BackgroundTask task) {
        Map<String, Object> state = parseState(task.getPublicStateJson());
        return new AdminBackgroundTaskView(
                task.getId(),
                task.getType(),
                task.getStatus(),
                task.getUserId(),
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
                readFailureCategory(state),
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

    private BackgroundTaskFailureCategory readFailureCategory(Map<String, Object> state) {
        String value = readStringState(state, "failureCategory");
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return BackgroundTaskFailureCategory.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
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

    private BackgroundTaskLeaseState resolveLeaseState(BackgroundTask task) {
        if (!StringUtils.hasText(task.getLeaseOwner()) || task.getLeaseExpiresAt() == null) {
            return BackgroundTaskLeaseState.NONE;
        }
        return task.getLeaseExpiresAt().isBefore(LocalDateTime.now())
                ? BackgroundTaskLeaseState.EXPIRED
                : BackgroundTaskLeaseState.ACTIVE;
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }
}
