package com.yoyuzh.files;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BackgroundTaskWorker {

    private static final int DEFAULT_BATCH_SIZE = 5;

    private final BackgroundTaskService backgroundTaskService;
    private final List<BackgroundTaskHandler> handlers;

    public BackgroundTaskWorker(BackgroundTaskService backgroundTaskService,
                                List<BackgroundTaskHandler> handlers) {
        this.backgroundTaskService = backgroundTaskService;
        this.handlers = List.copyOf(handlers);
    }

    @Scheduled(
            fixedDelayString = "${app.background-tasks.worker.fixed-delay-ms:30000}",
            initialDelayString = "${app.background-tasks.worker.initial-delay-ms:30000}"
    )
    public void runScheduledBatch() {
        processQueuedTasks(DEFAULT_BATCH_SIZE);
    }

    public int processQueuedTasks(int maxTasks) {
        int processedCount = 0;
        for (Long taskId : backgroundTaskService.findQueuedTaskIds(maxTasks)) {
            var claimedTask = backgroundTaskService.claimQueuedTask(taskId);
            if (claimedTask.isEmpty()) {
                continue;
            }

            execute(claimedTask.get());
            processedCount += 1;
        }
        return processedCount;
    }

    private void execute(BackgroundTask task) {
        try {
            BackgroundTaskHandler handler = findHandler(task);
            BackgroundTaskHandlerResult result = handler.handle(task);
            backgroundTaskService.markWorkerTaskCompleted(task.getId(), result.publicStatePatch());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            backgroundTaskService.markWorkerTaskFailed(task.getId(), message);
        }
    }

    private BackgroundTaskHandler findHandler(BackgroundTask task) {
        return handlers.stream()
                .filter(handler -> handler.supports(task.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No background task handler for " + task.getType()));
    }
}
