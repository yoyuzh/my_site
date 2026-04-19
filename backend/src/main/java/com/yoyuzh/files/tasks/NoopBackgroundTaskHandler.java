package com.yoyuzh.files.tasks;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Component
public class NoopBackgroundTaskHandler implements BackgroundTaskHandler {

    private static final Set<BackgroundTaskType> SUPPORTED_TYPES = Set.of();

    @Override
    public boolean supports(BackgroundTaskType type) {
        return SUPPORTED_TYPES.contains(type);
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task) {
        return new BackgroundTaskHandlerResult(Map.of(
                "worker", "noop",
                "message", "worker skeleton accepted task without running real file processing",
                "completedAt", LocalDateTime.now().toString()
        ));
    }
}
