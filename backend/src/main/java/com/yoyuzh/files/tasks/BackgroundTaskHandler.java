package com.yoyuzh.files.tasks;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

public interface BackgroundTaskHandler {

    boolean supports(BackgroundTaskType type);

    BackgroundTaskHandlerResult handle(BackgroundTask task);

    default BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        return handle(task);
    }
}
