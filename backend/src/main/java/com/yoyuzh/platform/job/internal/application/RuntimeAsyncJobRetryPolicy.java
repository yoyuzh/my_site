package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.AsyncJobRetryPolicy;
import org.springframework.stereotype.Service;

@Service
public class RuntimeAsyncJobRetryPolicy implements AsyncJobRetryPolicy {

    @Override
    public int resolveMaxAttempts(BackgroundTaskType type) {
        return switch (type) {
            case ARCHIVE -> 4;
            case EXTRACT -> 3;
            case MEDIA_META -> 2;
            default -> 1;
        };
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
        long baseDelaySeconds = switch (type) {
            case ARCHIVE -> 30L;
            case EXTRACT -> 45L;
            case MEDIA_META -> 15L;
            default -> 30L;
        };
        if (failureCategory == BackgroundTaskFailureCategory.RATE_LIMITED) {
            baseDelaySeconds *= 4L;
        } else if (failureCategory == BackgroundTaskFailureCategory.UNKNOWN) {
            baseDelaySeconds *= 2L;
        }
        long delay = baseDelaySeconds * (1L << Math.min(safeAttemptCount - 1, 2));
        return Math.min(delay, baseDelaySeconds * 4L);
    }
}
