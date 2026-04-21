package com.yoyuzh.platform.job.internal.application;

import java.util.Map;

@FunctionalInterface
public interface BackgroundTaskProgressReporter {

    void report(Map<String, Object> publicStatePatch);
}
