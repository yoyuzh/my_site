package com.yoyuzh.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskRepository;
import com.yoyuzh.files.tasks.BackgroundTaskStatus;
import com.yoyuzh.files.tasks.BackgroundTaskType;
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
class AdminTaskQueryServiceTest {

    @Mock
    private BackgroundTaskRepository backgroundTaskRepository;
    @Mock
    private UserRepository userRepository;

    private AdminTaskQueryService adminTaskQueryService;

    @BeforeEach
    void setUp() {
        adminTaskQueryService = new AdminTaskQueryService(backgroundTaskRepository, userRepository, new ObjectMapper());
    }

    @Test
    void shouldListTasksWithParsedDerivedFields() {
        User owner = createUser(1L, "alice", "alice@example.com");
        BackgroundTask task = createTask(11L, owner.getId(), BackgroundTaskStatus.RUNNING);
        task.setPublicStateJson("""
                {"failureCategory":"TRANSIENT_INFRASTRUCTURE","retryScheduled":true,"workerOwner":"media-worker-1"}
                """);
        task.setLeaseOwner("worker-a");
        task.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(1));
        when(backgroundTaskRepository.searchAdminTasks(eq("alice"), eq(BackgroundTaskType.MEDIA_META), eq(BackgroundTaskStatus.RUNNING), any(), eq(AdminTaskLeaseState.ACTIVE.name()), any(), any()))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        PageResponse<AdminTaskResponse> response = adminTaskQueryService.listTasks(
                0,
                10,
                "alice",
                BackgroundTaskType.MEDIA_META,
                BackgroundTaskStatus.RUNNING,
                com.yoyuzh.files.tasks.BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE,
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
        when(backgroundTaskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminTaskQueryService.getTask(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("task not found");
    }

    private User createUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private BackgroundTask createTask(Long id, Long userId, BackgroundTaskStatus status) {
        BackgroundTask task = new BackgroundTask();
        task.setId(id);
        task.setType(BackgroundTaskType.MEDIA_META);
        task.setStatus(status);
        task.setUserId(userId);
        task.setPublicStateJson("{}");
        task.setCorrelationId("task-" + id);
        task.setAttemptCount(1);
        task.setMaxAttempts(3);
        task.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
