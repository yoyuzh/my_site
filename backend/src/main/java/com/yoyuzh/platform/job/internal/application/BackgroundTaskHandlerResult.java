package com.yoyuzh.platform.job.internal.application;

import java.util.Map;

public record BackgroundTaskHandlerResult(Map<String, Object> publicStatePatch,
                                          boolean completed,
                                          Long nextRunDelaySeconds) {

    public BackgroundTaskHandlerResult(Map<String, Object> publicStatePatch) {
        this(publicStatePatch, true, null);
    }

    public static BackgroundTaskHandlerResult empty() {
        return new BackgroundTaskHandlerResult(Map.of(), true, null);
    }

    // nextRunDelaySeconds is a relative delay from now, not an absolute timestamp.
    public static BackgroundTaskHandlerResult reschedule(Map<String, Object> publicStatePatch, long nextRunDelaySeconds) {
        return new BackgroundTaskHandlerResult(publicStatePatch, false, Math.max(1L, nextRunDelaySeconds));
    }
}
