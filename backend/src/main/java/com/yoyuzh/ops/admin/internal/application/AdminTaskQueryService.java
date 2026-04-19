package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import com.yoyuzh.platform.job.api.AdminBackgroundTaskQuery;
import com.yoyuzh.platform.job.api.AdminBackgroundTaskView;
import com.yoyuzh.platform.job.api.BackgroundTaskAdminQueryApi;
import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskLeaseState;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.shared.kernel.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminTaskQueryService {

    private final BackgroundTaskAdminQueryApi backgroundTaskAdminQueryApi;
    private final IdentityUserDirectoryApi identityUserDirectoryApi;

    public PageResponse<AdminTaskResponse> listTasks(int page,
                                                     int size,
                                                     String userQuery,
                                                     BackgroundTaskType type,
                                                     BackgroundTaskStatus status,
                                                     BackgroundTaskFailureCategory failureCategory,
                                                     AdminTaskLeaseState leaseState) {
        PageResponse<AdminBackgroundTaskView> result = backgroundTaskAdminQueryApi.listTasks(new AdminBackgroundTaskQuery(
                page,
                size,
                userQuery,
                type,
                status,
                failureCategory,
                leaseState == null ? null : BackgroundTaskLeaseState.valueOf(leaseState.name())
        ));
        Map<Long, IdentityUserProfileSummary> ownerById = identityUserDirectoryApi.findProfilesByIds(
                result.items().stream()
                        .map(AdminBackgroundTaskView::userId)
                        .collect(Collectors.toSet())
        );
        return new PageResponse<>(
                result.items().stream()
                        .map(task -> toAdminTaskResponse(task, ownerById.get(task.userId())))
                        .toList(),
                result.total(),
                result.page(),
                result.size()
        );
    }

    public AdminTaskResponse getTask(Long taskId) {
        AdminBackgroundTaskView task = backgroundTaskAdminQueryApi.getTask(taskId);
        IdentityUserProfileSummary owner = identityUserDirectoryApi.findProfileById(task.userId()).orElse(null);
        return toAdminTaskResponse(task, owner);
    }

    private AdminTaskResponse toAdminTaskResponse(AdminBackgroundTaskView task, IdentityUserProfileSummary owner) {
        return new AdminTaskResponse(
                task.id(),
                task.type(),
                task.status(),
                task.userId(),
                owner == null ? null : owner.username(),
                owner == null ? null : owner.email(),
                task.publicStateJson(),
                task.correlationId(),
                task.errorMessage(),
                task.attemptCount(),
                task.maxAttempts(),
                task.nextRunAt(),
                task.leaseOwner(),
                task.leaseExpiresAt(),
                task.heartbeatAt(),
                task.createdAt(),
                task.updatedAt(),
                task.finishedAt(),
                task.failureCategory() == null ? null : task.failureCategory().name(),
                task.retryScheduled(),
                task.workerOwner(),
                task.leaseState() == null ? null : AdminTaskLeaseState.valueOf(task.leaseState().name())
        );
    }
}
