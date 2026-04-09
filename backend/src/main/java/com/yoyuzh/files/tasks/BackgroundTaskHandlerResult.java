package com.yoyuzh.files.tasks;

import java.util.Map;

public record BackgroundTaskHandlerResult(Map<String, Object> publicStatePatch) {

    public static BackgroundTaskHandlerResult empty() {
        return new BackgroundTaskHandlerResult(Map.of());
    }
}
