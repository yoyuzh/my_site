package com.yoyuzh.files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundTaskWorkerTest {

    @Mock
    private BackgroundTaskService backgroundTaskService;
    @Mock
    private BackgroundTaskHandler backgroundTaskHandler;

    private BackgroundTaskWorker backgroundTaskWorker;

    @BeforeEach
    void setUp() {
        backgroundTaskWorker = new BackgroundTaskWorker(backgroundTaskService, List.of(backgroundTaskHandler));
    }

    @Test
    void shouldClaimAndCompleteQueuedTaskThroughNoopHandler() {
        BackgroundTask task = createTask(1L, BackgroundTaskType.ARCHIVE, BackgroundTaskStatus.RUNNING);
        when(backgroundTaskService.findQueuedTaskIds(5)).thenReturn(List.of(1L));
        when(backgroundTaskService.claimQueuedTask(1L)).thenReturn(Optional.of(task));
        when(backgroundTaskHandler.supports(BackgroundTaskType.ARCHIVE)).thenReturn(true);
        when(backgroundTaskHandler.handle(task)).thenReturn(new BackgroundTaskHandlerResult(Map.of("worker", "noop")));

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isEqualTo(1);
        verify(backgroundTaskHandler).handle(task);
        verify(backgroundTaskService).markWorkerTaskCompleted(1L, Map.of("worker", "noop"));
    }

    @Test
    void shouldSkipTaskThatWasNotClaimed() {
        when(backgroundTaskService.findQueuedTaskIds(5)).thenReturn(List.of(1L));
        when(backgroundTaskService.claimQueuedTask(1L)).thenReturn(Optional.empty());

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isZero();
        verify(backgroundTaskHandler, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldMarkTaskFailedWhenHandlerThrows() {
        BackgroundTask task = createTask(2L, BackgroundTaskType.MEDIA_META, BackgroundTaskStatus.RUNNING);
        when(backgroundTaskService.findQueuedTaskIds(5)).thenReturn(List.of(2L));
        when(backgroundTaskService.claimQueuedTask(2L)).thenReturn(Optional.of(task));
        when(backgroundTaskHandler.supports(BackgroundTaskType.MEDIA_META)).thenReturn(true);
        when(backgroundTaskHandler.handle(task)).thenThrow(new IllegalStateException("media parser unavailable"));

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isEqualTo(1);
        verify(backgroundTaskService).markWorkerTaskFailed(2L, "media parser unavailable");
    }

    private BackgroundTask createTask(Long id, BackgroundTaskType type, BackgroundTaskStatus status) {
        BackgroundTask task = new BackgroundTask();
        task.setId(id);
        task.setType(type);
        task.setStatus(status);
        task.setUserId(7L);
        task.setPublicStateJson("{}");
        task.setPrivateStateJson("{}");
        return task;
    }
}
