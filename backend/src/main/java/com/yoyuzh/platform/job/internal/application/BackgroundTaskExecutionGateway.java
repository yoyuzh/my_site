package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface BackgroundTaskExecutionGateway {

    int requeueExpiredRunningTasks();

    List<Long> findQueuedTaskIds(int limit);

    List<Long> findQueuedTaskIdsByTypes(Collection<BackgroundTaskType> types, int limit);

    Optional<BackgroundTask> claimQueuedTask(Long id, String workerOwner, long leaseDurationSeconds);

    BackgroundTask markWorkerTaskProgress(Long id,
                                          String workerOwner,
                                          Map<String, Object> publicStatePatch,
                                          long leaseDurationSeconds);

    BackgroundTask markWorkerTaskCompleted(Long id,
                                           String workerOwner,
                                           Map<String, Object> publicStatePatch,
                                           long leaseDurationSeconds);

    BackgroundTask markWorkerTaskRequeued(Long id,
                                          String workerOwner,
                                          Map<String, Object> publicStatePatch,
                                          long nextRunDelaySeconds,
                                          long leaseDurationSeconds);

    BackgroundTask markWorkerTaskFailed(Long id,
                                        String workerOwner,
                                        String errorMessage,
                                        BackgroundTaskFailureCategory failureCategory,
                                        long leaseDurationSeconds);
}
