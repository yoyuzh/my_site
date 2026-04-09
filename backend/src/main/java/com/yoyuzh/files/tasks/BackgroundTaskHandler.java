package com.yoyuzh.files.tasks;

public interface BackgroundTaskHandler {

    boolean supports(BackgroundTaskType type);

    BackgroundTaskHandlerResult handle(BackgroundTask task);

    default BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        return handle(task);
    }
}
