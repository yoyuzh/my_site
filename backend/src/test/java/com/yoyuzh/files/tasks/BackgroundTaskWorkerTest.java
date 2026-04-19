package com.yoyuzh.files.tasks;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundTaskWorkerTest {

    @Mock
    private BackgroundTaskExecutionGateway backgroundTaskExecutionGateway;
    @Mock
    private BackgroundTaskHandler backgroundTaskHandler;

    private BackgroundTaskWorker backgroundTaskWorker;

    @BeforeEach
    void setUp() {
        backgroundTaskWorker = new BackgroundTaskWorker(backgroundTaskExecutionGateway, List.of(backgroundTaskHandler));
    }

    @Test
    void shouldClaimAndCompleteQueuedTaskThroughNoopHandler() {
        BackgroundTask task = createTask(1L, BackgroundTaskType.ARCHIVE, BackgroundTaskStatus.RUNNING);
        when(backgroundTaskExecutionGateway.findQueuedTaskIds(5)).thenReturn(List.of(1L));
        when(backgroundTaskExecutionGateway.claimQueuedTask(eq(1L), anyString(), anyLong())).thenReturn(Optional.of(task));
        when(backgroundTaskHandler.supports(BackgroundTaskType.ARCHIVE)).thenReturn(true);
        when(backgroundTaskHandler.handle(eq(task), any(BackgroundTaskProgressReporter.class)))
                .thenReturn(new BackgroundTaskHandlerResult(Map.of("worker", "noop")));

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isEqualTo(1);
        verify(backgroundTaskExecutionGateway).markWorkerTaskProgress(eq(1L), anyString(), eq(Map.of("phase", "archiving")), anyLong());
        verify(backgroundTaskHandler).handle(eq(task), any(BackgroundTaskProgressReporter.class));
        verify(backgroundTaskExecutionGateway).markWorkerTaskCompleted(eq(1L), anyString(), eq(Map.of("worker", "noop")), anyLong());
    }

    @Test
    void shouldSkipTaskThatWasNotClaimed() {
        when(backgroundTaskExecutionGateway.findQueuedTaskIds(5)).thenReturn(List.of(1L));
        when(backgroundTaskExecutionGateway.claimQueuedTask(eq(1L), anyString(), anyLong())).thenReturn(Optional.empty());

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isZero();
        verify(backgroundTaskHandler, never()).handle(any(BackgroundTask.class), any(BackgroundTaskProgressReporter.class));
    }

    @Test
    void shouldMarkTaskFailedWhenHandlerThrows() {
        BackgroundTask task = createTask(2L, BackgroundTaskType.MEDIA_META, BackgroundTaskStatus.RUNNING);
        when(backgroundTaskExecutionGateway.findQueuedTaskIds(5)).thenReturn(List.of(2L));
        when(backgroundTaskExecutionGateway.claimQueuedTask(eq(2L), anyString(), anyLong())).thenReturn(Optional.of(task));
        when(backgroundTaskHandler.supports(BackgroundTaskType.MEDIA_META)).thenReturn(true);
        when(backgroundTaskHandler.handle(eq(task), any(BackgroundTaskProgressReporter.class)))
                .thenThrow(new IllegalStateException("media parser unavailable"));

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isEqualTo(1);
        verify(backgroundTaskExecutionGateway).markWorkerTaskProgress(eq(2L), anyString(), eq(Map.of("phase", "extracting-metadata")), anyLong());
        verify(backgroundTaskExecutionGateway).markWorkerTaskFailed(
                eq(2L),
                anyString(),
                eq("media parser unavailable"),
                eq(BackgroundTaskFailureCategory.DATA_STATE),
                anyLong()
        );
    }

    @Test
    void shouldAutoRetryUnexpectedWorkerFailure() {
        BackgroundTask task = createTask(3L, BackgroundTaskType.ARCHIVE, BackgroundTaskStatus.RUNNING);
        when(backgroundTaskExecutionGateway.findQueuedTaskIds(5)).thenReturn(List.of(3L));
        when(backgroundTaskExecutionGateway.claimQueuedTask(eq(3L), anyString(), anyLong())).thenReturn(Optional.of(task));
        when(backgroundTaskHandler.supports(BackgroundTaskType.ARCHIVE)).thenReturn(true);
        when(backgroundTaskHandler.handle(eq(task), any(BackgroundTaskProgressReporter.class)))
                .thenThrow(new RuntimeException("storage timeout"));

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isEqualTo(1);
        verify(backgroundTaskExecutionGateway).markWorkerTaskFailed(
                eq(3L),
                anyString(),
                eq("storage timeout"),
                eq(BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE),
                anyLong()
        );
    }

    @Test
    void shouldClassifyRateLimitedFailureSeparately() {
        BackgroundTask task = createTask(4L, BackgroundTaskType.EXTRACT, BackgroundTaskStatus.RUNNING);
        when(backgroundTaskExecutionGateway.findQueuedTaskIds(5)).thenReturn(List.of(4L));
        when(backgroundTaskExecutionGateway.claimQueuedTask(eq(4L), anyString(), anyLong())).thenReturn(Optional.of(task));
        when(backgroundTaskHandler.supports(BackgroundTaskType.EXTRACT)).thenReturn(true);
        when(backgroundTaskHandler.handle(eq(task), any(BackgroundTaskProgressReporter.class)))
                .thenThrow(new RuntimeException("429 Too Many Requests"));

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isEqualTo(1);
        verify(backgroundTaskExecutionGateway).markWorkerTaskFailed(
                eq(4L),
                anyString(),
                eq("429 Too Many Requests"),
                eq(BackgroundTaskFailureCategory.RATE_LIMITED),
                anyLong()
        );
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
