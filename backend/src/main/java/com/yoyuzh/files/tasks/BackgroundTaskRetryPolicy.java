package com.yoyuzh.files.tasks;

import org.springframework.stereotype.Component;

@Component
public class BackgroundTaskRetryPolicy {

    public int resolveMaxAttempts(BackgroundTaskType type) {
        return switch (type) {
            case ARCHIVE -> 4;
            case EXTRACT -> 3;
            case MEDIA_META -> 2;
            default -> 1;
        };
    }

    public boolean hasRemainingAttempts(BackgroundTask task) {
        return task.getAttemptCount() != null
                && task.getMaxAttempts() != null
                && task.getAttemptCount() < task.getMaxAttempts();
    }

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
