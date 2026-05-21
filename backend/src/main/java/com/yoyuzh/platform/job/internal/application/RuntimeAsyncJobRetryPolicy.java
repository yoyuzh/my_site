package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.AsyncJobRetryPolicy;
import org.springframework.stereotype.Service;

@Service
public class RuntimeAsyncJobRetryPolicy implements AsyncJobRetryPolicy {

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
        long baseDelaySeconds = baseDelaySeconds(type);
        if (failureCategory == BackgroundTaskFailureCategory.RATE_LIMITED) {
            baseDelaySeconds *= 4L;
        } else if (failureCategory == BackgroundTaskFailureCategory.UNKNOWN) {
            baseDelaySeconds *= 2L;
        }
        long delay = baseDelaySeconds * (1L << Math.min(safeAttemptCount - 1, 2));
        return Math.min(delay, baseDelaySeconds * 4L);
    }

    private long baseDelaySeconds(BackgroundTaskType type) {
        if (type == BackgroundTaskType.EXTRACT) {
            return 45L;
        }
        if (type == BackgroundTaskType.MEDIA_META) {
            return 15L;
        }
        if (type == BackgroundTaskType.BLOB_UPLOAD) {
            return 10L;
        }
        return 30L;
    }
}
