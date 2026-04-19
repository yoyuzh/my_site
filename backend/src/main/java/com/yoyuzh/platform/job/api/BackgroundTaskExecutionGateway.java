package com.yoyuzh.platform.job.api;

import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskFailureCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface BackgroundTaskExecutionGateway {

    int requeueExpiredRunningTasks();

    List<Long> findQueuedTaskIds(int limit);

    Optional<BackgroundTask> claimQueuedTask(Long id, String workerOwner, long leaseDurationSeconds);

    BackgroundTask markWorkerTaskProgress(Long id,
                                          String workerOwner,
                                          Map<String, Object> publicStatePatch,
                                          long leaseDurationSeconds);

    BackgroundTask markWorkerTaskCompleted(Long id,
                                           String workerOwner,
                                           Map<String, Object> publicStatePatch,
                                           long leaseDurationSeconds);

    BackgroundTask markWorkerTaskFailed(Long id,
                                        String workerOwner,
                                        String errorMessage,
                                        BackgroundTaskFailureCategory failureCategory,
                                        long leaseDurationSeconds);
}
