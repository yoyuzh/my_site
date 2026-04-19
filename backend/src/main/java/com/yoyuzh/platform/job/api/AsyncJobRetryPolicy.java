package com.yoyuzh.platform.job.api;

import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskFailureCategory;
import com.yoyuzh.files.tasks.BackgroundTaskType;

public interface AsyncJobRetryPolicy {

    int resolveMaxAttempts(BackgroundTaskType type);

    boolean hasRemainingAttempts(BackgroundTask task);

    long resolveRetryDelaySeconds(BackgroundTaskType type,
                                  BackgroundTaskFailureCategory failureCategory,
                                  Integer attemptCount);
}
