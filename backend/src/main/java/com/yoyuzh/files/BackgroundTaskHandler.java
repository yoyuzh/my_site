package com.yoyuzh.files;

public interface BackgroundTaskHandler {

    boolean supports(BackgroundTaskType type);

    BackgroundTaskHandlerResult handle(BackgroundTask task);
}
