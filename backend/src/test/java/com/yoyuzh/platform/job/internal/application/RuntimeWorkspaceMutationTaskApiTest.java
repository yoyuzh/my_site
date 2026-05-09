package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.workspace.api.FileDeleteMode;
import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspaceMutationTaskApiTest {

    @Mock
    private BackgroundTaskService backgroundTaskService;
    @Mock
    private BackgroundTaskWorker backgroundTaskWorker;

    private RuntimeWorkspaceMutationTaskApi mutationTaskApi;

    @BeforeEach
    void setUp() {
        mutationTaskApi = new RuntimeWorkspaceMutationTaskApi(backgroundTaskService, backgroundTaskWorker);
    }

    @Test
    void shouldWakeLightweightWorkerAfterMoveTaskIsQueued() {
        when(backgroundTaskService.createQueuedTaskByUserId(anyLong(), eq(BackgroundTaskType.WORKSPACE_MUTATION), any(), any(), anyString()))
                .thenReturn(task(21L));

        mutationTaskApi.enqueueMove(7L, List.of(1L, 2L), "/target", WorkspaceMoveConflictStrategy.AUTO_RENAME);

        verify(backgroundTaskWorker).wakeLightweightTasks();
        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(backgroundTaskService).createQueuedTaskByUserId(
                eq(7L),
                eq(BackgroundTaskType.WORKSPACE_MUTATION),
                stateCaptor.capture(),
                any(),
                anyString()
        );
        assertThat(stateCaptor.getValue()).containsEntry("operation", "MOVE");
    }

    @Test
    void shouldDeduplicateMoveFileIdsBeforeQueueing() {
        when(backgroundTaskService.createQueuedTaskByUserId(anyLong(), eq(BackgroundTaskType.WORKSPACE_MUTATION), any(), any(), anyString()))
                .thenReturn(task(24L));

        mutationTaskApi.enqueueMove(7L, List.of(1L, 1L, 2L), "/target", null);

        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(backgroundTaskService).createQueuedTaskByUserId(
                eq(7L),
                eq(BackgroundTaskType.WORKSPACE_MUTATION),
                stateCaptor.capture(),
                any(),
                anyString()
        );
        assertThat(stateCaptor.getValue()).containsEntry("fileIds", List.of(1L, 2L));
        assertThat(stateCaptor.getValue()).containsEntry("totalItems", 2);
    }

    @Test
    void shouldWakeLightweightWorkerAfterDeleteTaskIsQueued() {
        when(backgroundTaskService.createQueuedTaskByUserId(anyLong(), eq(BackgroundTaskType.WORKSPACE_MUTATION), any(), any(), anyString()))
                .thenReturn(task(22L));

        mutationTaskApi.enqueueDelete(7L, List.of(3L), FileDeleteMode.RECYCLE);

        verify(backgroundTaskWorker).wakeLightweightTasks();
    }

    @Test
    void shouldWakeLightweightWorkerAfterRenameTaskIsQueued() {
        when(backgroundTaskService.createQueuedTaskByUserId(anyLong(), eq(BackgroundTaskType.WORKSPACE_MUTATION), any(), any(), anyString()))
                .thenReturn(task(23L));

        mutationTaskApi.enqueueRename(7L, 4L, "new-name.txt");

        verify(backgroundTaskWorker).wakeLightweightTasks();
    }

    @Test
    void shouldRejectInvalidMoveCommandBeforeQueueing() {
        assertThatThrownBy(() -> mutationTaskApi.enqueueMove(7L, List.of(), "/target", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("move task missing fileIds");
        assertThatThrownBy(() -> mutationTaskApi.enqueueMove(7L, Arrays.asList(1L, null), "/target", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid fileId");
        assertThatThrownBy(() -> mutationTaskApi.enqueueMove(7L, List.of(1L), " ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("move task missing targetPath");

        verify(backgroundTaskService, never()).createQueuedTaskByUserId(anyLong(), any(), any(), any(), anyString());
        verify(backgroundTaskWorker, never()).wakeLightweightTasks();
    }

    @Test
    void shouldRejectInvalidDeleteCommandBeforeQueueing() {
        assertThatThrownBy(() -> mutationTaskApi.enqueueDelete(7L, null, FileDeleteMode.RECYCLE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("delete task missing fileIds");
        assertThatThrownBy(() -> mutationTaskApi.enqueueDelete(7L, Arrays.asList(1L, null), FileDeleteMode.RECYCLE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid fileId");

        verify(backgroundTaskService, never()).createQueuedTaskByUserId(anyLong(), any(), any(), any(), anyString());
        verify(backgroundTaskWorker, never()).wakeLightweightTasks();
    }

    @Test
    void shouldRejectInvalidRenameCommandBeforeQueueing() {
        assertThatThrownBy(() -> mutationTaskApi.enqueueRename(7L, null, "new-name.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rename task missing fileId");
        assertThatThrownBy(() -> mutationTaskApi.enqueueRename(7L, 4L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rename task missing filename");

        verify(backgroundTaskService, never()).createQueuedTaskByUserId(anyLong(), any(), any(), any(), anyString());
        verify(backgroundTaskWorker, never()).wakeLightweightTasks();
    }

    private BackgroundTask task(Long id) {
        BackgroundTask task = new BackgroundTask();
        task.setId(id);
        task.setType(BackgroundTaskType.WORKSPACE_MUTATION);
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setUserId(7L);
        task.setPublicStateJson("{}");
        task.setPrivateStateJson("{}");
        task.setCorrelationId("workspace-mutation:test:" + id);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
