package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.platform.job.api.AsyncJobRetryPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BackgroundTaskRetryPolicy {

    private final AsyncJobRetryPolicy asyncJobRetryPolicy;

    @Autowired
    public BackgroundTaskRetryPolicy(AsyncJobRetryPolicy asyncJobRetryPolicy) {
        this.asyncJobRetryPolicy = asyncJobRetryPolicy;
    }

    public BackgroundTaskRetryPolicy() {
        this.asyncJobRetryPolicy = new AsyncJobRetryPolicy() {
            @Override
            public int resolveMaxAttempts(BackgroundTaskType type) {
                if (type == BackgroundTaskType.ARCHIVE) {
                    return 4;
                }
                if (type == BackgroundTaskType.EXTRACT) {
                    return 3;
                }
                if (type == BackgroundTaskType.MEDIA_META) {
                    return 2;
                }
                if (type == BackgroundTaskType.BLOB_UPLOAD) {
                    return 3;
                }
                return 1;
            }

            @Override
            public boolean hasRemainingAttempts(Integer attemptCount, Integer maxAttempts) {
                return attemptCount != null
                        && maxAttempts != null
                        && attemptCount < maxAttempts;
            }

            @Override
            public long resolveRetryDelaySeconds(BackgroundTaskType type,
                                                 BackgroundTaskFailureCategory failureCategory,
                                                 Integer attemptCount) {
                int safeAttemptCount = attemptCount == null ? 1 : Math.max(1, attemptCount);
                long baseDelaySeconds;
                if (type == BackgroundTaskType.EXTRACT) {
                    baseDelaySeconds = 45L;
                } else if (type == BackgroundTaskType.MEDIA_META) {
                    baseDelaySeconds = 15L;
                } else if (type == BackgroundTaskType.BLOB_UPLOAD) {
                    baseDelaySeconds = 10L;
                } else {
                    baseDelaySeconds = 30L;
                }
                if (failureCategory == BackgroundTaskFailureCategory.RATE_LIMITED) {
                    baseDelaySeconds *= 4L;
                } else if (failureCategory == BackgroundTaskFailureCategory.UNKNOWN) {
                    baseDelaySeconds *= 2L;
                }
                long delay = baseDelaySeconds * (1L << Math.min(safeAttemptCount - 1, 2));
                return Math.min(delay, baseDelaySeconds * 4L);
            }
        };
    }

    public int resolveMaxAttempts(BackgroundTaskType type) {
        return asyncJobRetryPolicy.resolveMaxAttempts(type);
    }

    public boolean hasRemainingAttempts(BackgroundTask task) {
        return asyncJobRetryPolicy.hasRemainingAttempts(task.getAttemptCount(), task.getMaxAttempts());
    }

    public long resolveRetryDelaySeconds(BackgroundTaskType type,
                                         BackgroundTaskFailureCategory failureCategory,
                                         Integer attemptCount) {
        return asyncJobRetryPolicy.resolveRetryDelaySeconds(type, failureCategory, attemptCount);
    }
}
