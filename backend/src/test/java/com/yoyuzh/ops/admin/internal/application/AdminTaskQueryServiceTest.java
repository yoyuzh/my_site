package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import com.yoyuzh.platform.job.api.AdminBackgroundTaskView;
import com.yoyuzh.platform.job.api.BackgroundTaskAdminQueryApi;
import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskLeaseState;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTaskQueryServiceTest {

    @Mock
    private BackgroundTaskAdminQueryApi backgroundTaskAdminQueryApi;
    @Mock
    private IdentityUserDirectoryApi identityUserDirectoryApi;

    private AdminTaskQueryService adminTaskQueryService;

    @BeforeEach
    void setUp() {
        adminTaskQueryService = new AdminTaskQueryService(backgroundTaskAdminQueryApi, identityUserDirectoryApi);
    }

    @Test
    void shouldListTasksWithParsedDerivedFields() {
        IdentityUserProfileSummary owner = new IdentityUserProfileSummary(1L, "alice", "alice@example.com");
        AdminBackgroundTaskView task = createTask(11L, owner.id(), BackgroundTaskStatus.RUNNING);
        when(backgroundTaskAdminQueryApi.listTasks(any()))
                .thenReturn(new PageResponse<>(List.of(task), 1L, 0, 10));
        when(identityUserDirectoryApi.findProfilesByIds(any()))
                .thenReturn(Map.of(owner.id(), owner));

        PageResponse<AdminTaskResponse> response = adminTaskQueryService.listTasks(
                0,
                10,
                "alice",
                BackgroundTaskType.MEDIA_META,
                BackgroundTaskStatus.RUNNING,
                BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE,
                AdminTaskLeaseState.ACTIVE
        );

        assertThat(response.total()).isEqualTo(1L);
        AdminTaskResponse first = response.items().get(0);
        assertThat(first.ownerUsername()).isEqualTo("alice");
        assertThat(first.failureCategory()).isEqualTo("TRANSIENT_INFRASTRUCTURE");
        assertThat(first.retryScheduled()).isTrue();
        assertThat(first.workerOwner()).isEqualTo("media-worker-1");
        assertThat(first.leaseState()).isEqualTo(AdminTaskLeaseState.ACTIVE);
    }

    @Test
    void shouldThrowWhenTaskNotFound() {
        when(backgroundTaskAdminQueryApi.getTask(99L))
                .thenThrow(new BusinessException(com.yoyuzh.shared.kernel.ErrorCode.FILE_NOT_FOUND, "task not found"));

        assertThatThrownBy(() -> adminTaskQueryService.getTask(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("task not found");
    }

    @Test
    void shouldLoadOwnerViaIdentityApiWhenReadingSingleTask() {
        IdentityUserProfileSummary owner = new IdentityUserProfileSummary(2L, "bob", "bob@example.com");
        AdminBackgroundTaskView task = createTask(21L, owner.id(), BackgroundTaskStatus.QUEUED);
        when(backgroundTaskAdminQueryApi.getTask(21L)).thenReturn(task);
        when(identityUserDirectoryApi.findProfileById(owner.id())).thenReturn(Optional.of(owner));

        AdminTaskResponse response = adminTaskQueryService.getTask(21L);

        assertThat(response.ownerUsername()).isEqualTo("bob");
        assertThat(response.ownerEmail()).isEqualTo("bob@example.com");
    }

    private AdminBackgroundTaskView createTask(Long id, Long userId, BackgroundTaskStatus status) {
        return new AdminBackgroundTaskView(
                id,
                BackgroundTaskType.MEDIA_META,
                status,
                userId,
                "{\"failureCategory\":\"TRANSIENT_INFRASTRUCTURE\",\"retryScheduled\":true,\"workerOwner\":\"media-worker-1\"}",
                "task-" + id,
                null,
                1,
                3,
                null,
                "worker-a",
                LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now(),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now(),
                null,
                BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE,
                true,
                "media-worker-1",
                BackgroundTaskLeaseState.ACTIVE
        );
    }
}
