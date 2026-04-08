package com.yoyuzh.files;

import java.util.Map;

public record BackgroundTaskHandlerResult(Map<String, Object> publicStatePatch) {

    public static BackgroundTaskHandlerResult empty() {
        return new BackgroundTaskHandlerResult(Map.of());
    }
}
