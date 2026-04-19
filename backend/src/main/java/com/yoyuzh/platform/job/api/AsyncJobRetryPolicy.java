package com.yoyuzh.platform.job.api;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

public interface AsyncJobRetryPolicy {

    int resolveMaxAttempts(BackgroundTaskType type);

    boolean hasRemainingAttempts(Integer attemptCount, Integer maxAttempts);

    long resolveRetryDelaySeconds(BackgroundTaskType type,
                                  BackgroundTaskFailureCategory failureCategory,
                                  Integer attemptCount);
}
