package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskExecutionService;
import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeBackgroundTaskExecutionGatewayTest {

    @Mock
    private BackgroundTaskExecutionService backgroundTaskExecutionService;

    @Test
    void shouldDelegateExecutionOperations() {
        RuntimeBackgroundTaskExecutionGateway gateway = new RuntimeBackgroundTaskExecutionGateway(backgroundTaskExecutionService);
        BackgroundTask task = new BackgroundTask();
        when(backgroundTaskExecutionService.requeueExpiredRunningTasks()).thenReturn(2);
        when(backgroundTaskExecutionService.findQueuedTaskIds(5)).thenReturn(List.of(1L, 2L));
        when(backgroundTaskExecutionService.claimQueuedTask(1L, "worker-a", 120L)).thenReturn(Optional.of(task));
        when(backgroundTaskExecutionService.markWorkerTaskProgress(1L, "worker-a", Map.of("phase", "running"), 120L)).thenReturn(task);
        when(backgroundTaskExecutionService.markWorkerTaskCompleted(1L, "worker-a", Map.of("done", true), 120L)).thenReturn(task);
        when(backgroundTaskExecutionService.markWorkerTaskFailed(1L, "worker-a", "boom", BackgroundTaskFailureCategory.UNKNOWN, 120L)).thenReturn(task);

        assertThat(gateway.requeueExpiredRunningTasks()).isEqualTo(2);
        assertThat(gateway.findQueuedTaskIds(5)).containsExactly(1L, 2L);
        assertThat(gateway.claimQueuedTask(1L, "worker-a", 120L)).containsSame(task);
        assertThat(gateway.markWorkerTaskProgress(1L, "worker-a", Map.of("phase", "running"), 120L)).isSameAs(task);
        assertThat(gateway.markWorkerTaskCompleted(1L, "worker-a", Map.of("done", true), 120L)).isSameAs(task);
        assertThat(gateway.markWorkerTaskFailed(1L, "worker-a", "boom", BackgroundTaskFailureCategory.UNKNOWN, 120L)).isSameAs(task);

        verify(backgroundTaskExecutionService).requeueExpiredRunningTasks();
        verify(backgroundTaskExecutionService).findQueuedTaskIds(5);
        verify(backgroundTaskExecutionService).claimQueuedTask(1L, "worker-a", 120L);
        verify(backgroundTaskExecutionService).markWorkerTaskProgress(1L, "worker-a", Map.of("phase", "running"), 120L);
        verify(backgroundTaskExecutionService).markWorkerTaskCompleted(1L, "worker-a", Map.of("done", true), 120L);
        verify(backgroundTaskExecutionService).markWorkerTaskFailed(1L, "worker-a", "boom", BackgroundTaskFailureCategory.UNKNOWN, 120L);
    }
}
