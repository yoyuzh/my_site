package com.yoyuzh.files.tasks;

import java.util.Map;

@FunctionalInterface
public interface BackgroundTaskProgressReporter {

    void report(Map<String, Object> publicStatePatch);
}
