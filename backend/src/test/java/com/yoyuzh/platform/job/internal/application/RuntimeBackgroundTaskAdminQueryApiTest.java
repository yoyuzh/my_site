package com.yoyuzh.platform.job.internal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.platform.job.internal.infra.BackgroundTaskRepository;
import com.yoyuzh.platform.job.api.AdminBackgroundTaskQuery;
import com.yoyuzh.platform.job.api.AdminBackgroundTaskView;
import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskLeaseState;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeBackgroundTaskAdminQueryApiTest {

    @Mock
    private BackgroundTaskRepository backgroundTaskRepository;

    private RuntimeBackgroundTaskAdminQueryApi runtimeBackgroundTaskAdminQueryApi;

    @BeforeEach
    void setUp() {
        runtimeBackgroundTaskAdminQueryApi = new RuntimeBackgroundTaskAdminQueryApi(backgroundTaskRepository, new ObjectMapper());
    }

    @Test
    void shouldMapDerivedStateAndLeaseFlags() {
        BackgroundTask task = createTask(12L, BackgroundTaskStatus.RUNNING);
        task.setPublicStateJson("""
                {"failureCategory":"TRANSIENT_INFRASTRUCTURE","retryScheduled":true,"workerOwner":"worker-1"}
                """);
        task.setLeaseOwner("worker-a");
        task.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(2));

        when(backgroundTaskRepository.searchAdminTasks(
                eq("alice"),
                eq(BackgroundTaskType.MEDIA_META),
                eq(BackgroundTaskStatus.RUNNING),
                any(),
                eq(BackgroundTaskLeaseState.ACTIVE.name()),
                any(),
                any()
        )).thenReturn(new PageImpl<>(List.of(task)));

        PageResponse<AdminBackgroundTaskView> result = runtimeBackgroundTaskAdminQueryApi.listTasks(new AdminBackgroundTaskQuery(
                0,
                10,
                "alice",
                BackgroundTaskType.MEDIA_META,
                BackgroundTaskStatus.RUNNING,
                BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE,
                BackgroundTaskLeaseState.ACTIVE
        ));

        assertThat(result.total()).isEqualTo(1L);
        AdminBackgroundTaskView first = result.items().get(0);
        assertThat(first.failureCategory()).isEqualTo(BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE);
        assertThat(first.retryScheduled()).isTrue();
        assertThat(first.workerOwner()).isEqualTo("worker-1");
        assertThat(first.leaseState()).isEqualTo(BackgroundTaskLeaseState.ACTIVE);
    }

    @Test
    void shouldReturnNoneLeaseStateWhenLeaseIsEmpty() {
        BackgroundTask task = createTask(15L, BackgroundTaskStatus.QUEUED);
        task.setPublicStateJson("not-json");
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        when(backgroundTaskRepository.findById(15L)).thenReturn(Optional.of(task));

        AdminBackgroundTaskView result = runtimeBackgroundTaskAdminQueryApi.getTask(15L);

        assertThat(result.failureCategory()).isNull();
        assertThat(result.retryScheduled()).isNull();
        assertThat(result.leaseState()).isEqualTo(BackgroundTaskLeaseState.NONE);
    }

    @Test
    void shouldThrowWhenTaskDoesNotExist() {
        when(backgroundTaskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runtimeBackgroundTaskAdminQueryApi.getTask(404L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND))
                .hasMessageContaining("task not found");
    }

    private BackgroundTask createTask(Long id, BackgroundTaskStatus status) {
        BackgroundTask task = new BackgroundTask();
        task.setId(id);
        task.setType(BackgroundTaskType.MEDIA_META);
        task.setStatus(status);
        task.setUserId(1L);
        task.setPublicStateJson("{}");
        task.setCorrelationId("task-" + id);
        task.setAttemptCount(1);
        task.setMaxAttempts(3);
        task.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
