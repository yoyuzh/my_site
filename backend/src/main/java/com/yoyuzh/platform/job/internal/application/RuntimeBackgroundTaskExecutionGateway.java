package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.platform.job.internal.application.BackgroundTaskExecutionGateway;
import com.yoyuzh.platform.job.internal.application.BackgroundTaskExecutionService;
import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RuntimeBackgroundTaskExecutionGateway implements BackgroundTaskExecutionGateway {

    private final BackgroundTaskExecutionService backgroundTaskExecutionService;

    @Override
    public int requeueExpiredRunningTasks() {
        return backgroundTaskExecutionService.requeueExpiredRunningTasks();
    }

    @Override
    public List<Long> findQueuedTaskIds(int limit) {
        return backgroundTaskExecutionService.findQueuedTaskIds(limit);
    }

    @Override
    public Optional<BackgroundTask> claimQueuedTask(Long id, String workerOwner, long leaseDurationSeconds) {
        return backgroundTaskExecutionService.claimQueuedTask(id, workerOwner, leaseDurationSeconds);
    }

    @Override
    public BackgroundTask markWorkerTaskProgress(Long id,
                                                 String workerOwner,
                                                 Map<String, Object> publicStatePatch,
                                                 long leaseDurationSeconds) {
        return backgroundTaskExecutionService.markWorkerTaskProgress(id, workerOwner, publicStatePatch, leaseDurationSeconds);
    }

    @Override
    public BackgroundTask markWorkerTaskCompleted(Long id,
                                                  String workerOwner,
                                                  Map<String, Object> publicStatePatch,
                                                  long leaseDurationSeconds) {
        return backgroundTaskExecutionService.markWorkerTaskCompleted(id, workerOwner, publicStatePatch, leaseDurationSeconds);
    }

    @Override
    public BackgroundTask markWorkerTaskRequeued(Long id,
                                                 String workerOwner,
                                                 Map<String, Object> publicStatePatch,
                                                 long nextRunDelaySeconds,
                                                 long leaseDurationSeconds) {
        return backgroundTaskExecutionService.markWorkerTaskRequeued(
                id,
                workerOwner,
                publicStatePatch,
                nextRunDelaySeconds,
                leaseDurationSeconds
        );
    }

    @Override
    public BackgroundTask markWorkerTaskFailed(Long id,
                                               String workerOwner,
                                               String errorMessage,
                                               BackgroundTaskFailureCategory failureCategory,
                                               long leaseDurationSeconds) {
        return backgroundTaskExecutionService.markWorkerTaskFailed(
                id,
                workerOwner,
                errorMessage,
                failureCategory,
                leaseDurationSeconds
        );
    }
}
