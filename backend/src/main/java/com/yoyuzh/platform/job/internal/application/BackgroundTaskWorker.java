package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.shared.kernel.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Component
public class BackgroundTaskWorker {

    private static final int DEFAULT_BATCH_SIZE = 5;
    private static final long DEFAULT_LEASE_DURATION_SECONDS = 120L;

    private final BackgroundTaskExecutionGateway backgroundTaskExecutionGateway;
    private final List<BackgroundTaskHandler> handlers;
    private final String workerOwner;

    public BackgroundTaskWorker(BackgroundTaskExecutionGateway backgroundTaskExecutionGateway,
                                List<BackgroundTaskHandler> handlers) {
        this.backgroundTaskExecutionGateway = backgroundTaskExecutionGateway;
        this.handlers = List.copyOf(handlers);
        this.workerOwner = UUID.randomUUID().toString().replace("-", "");
    }

    @Scheduled(
            fixedDelayString = "${app.background-tasks.worker.fixed-delay-ms:30000}",
            initialDelayString = "${app.background-tasks.worker.initial-delay-ms:30000}"
    )
    public void runScheduledBatch() {
        processQueuedTasks(DEFAULT_BATCH_SIZE);
    }

    public int processQueuedTasks(int maxTasks) {
        backgroundTaskExecutionGateway.requeueExpiredRunningTasks();
        int processedCount = 0;
        for (Long taskId : backgroundTaskExecutionGateway.findQueuedTaskIds(maxTasks)) {
            var claimedTask = backgroundTaskExecutionGateway.claimQueuedTask(taskId, workerOwner, DEFAULT_LEASE_DURATION_SECONDS);
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
            backgroundTaskExecutionGateway.markWorkerTaskProgress(
                    task.getId(),
                    workerOwner,
                    Map.of(BackgroundTaskStateKeys.PHASE, resolveRunningPhase(task.getType())),
                    DEFAULT_LEASE_DURATION_SECONDS
            );
            BackgroundTaskHandler handler = findHandler(task);
            BackgroundTaskHandlerResult result = handler.handle(task, publicStatePatch ->
                    backgroundTaskExecutionGateway.markWorkerTaskProgress(
                            task.getId(),
                            workerOwner,
                            publicStatePatch,
                            DEFAULT_LEASE_DURATION_SECONDS
                    ));
            backgroundTaskExecutionGateway.markWorkerTaskCompleted(
                    task.getId(),
                    workerOwner,
                    result.publicStatePatch(),
                    DEFAULT_LEASE_DURATION_SECONDS
            );
        } catch (BackgroundTaskLeaseLostException ignored) {
            // Another worker reclaimed the task after this worker stopped heartbeating.
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            try {
                backgroundTaskExecutionGateway.markWorkerTaskFailed(
                        task.getId(),
                        workerOwner,
                        message,
                        classifyFailure(ex),
                        DEFAULT_LEASE_DURATION_SECONDS
                );
            } catch (BackgroundTaskLeaseLostException ignored) {
                // Lease already moved to another worker; keep current worker from overwriting state.
            }
        }
    }

    private String resolveRunningPhase(BackgroundTaskType type) {
        return switch (type) {
            case ARCHIVE -> "archiving";
            case EXTRACT -> "extracting";
            case MEDIA_META -> "extracting-metadata";
            case STORAGE_POLICY_MIGRATION -> "planning-storage-policy-migration";
            default -> "running";
        };
    }

    private BackgroundTaskHandler findHandler(BackgroundTask task) {
        return handlers.stream()
                .filter(handler -> handler.supports(task.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No background task handler for " + task.getType()));
    }

    private BackgroundTaskFailureCategory classifyFailure(Throwable throwable) {
        if (containsRateLimitSignal(throwable)) {
            return BackgroundTaskFailureCategory.RATE_LIMITED;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BusinessException || current instanceof IllegalArgumentException) {
                return BackgroundTaskFailureCategory.UNSUPPORTED_INPUT;
            }
            if (current instanceof IllegalStateException) {
                return BackgroundTaskFailureCategory.DATA_STATE;
            }
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException
                    || current instanceof ConnectException
                    || current instanceof IOException
                    || current instanceof UncheckedIOException) {
                return BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE;
            }
            current = current.getCause();
        }
        if (containsTransientInfrastructureSignal(throwable)) {
            return BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE;
        }
        return BackgroundTaskFailureCategory.UNKNOWN;
    }

    private boolean containsRateLimitSignal(Throwable throwable) {
        String message = collectMessages(throwable);
        return message.contains("429")
                || message.contains("too many requests")
                || message.contains("rate limit")
                || message.contains("throttle");
    }

    private boolean containsTransientInfrastructureSignal(Throwable throwable) {
        String message = collectMessages(throwable);
        return message.contains("timeout")
                || message.contains("temporarily unavailable")
                || message.contains("connection reset")
                || message.contains("broken pipe")
                || message.contains("connection refused");
    }

    private String collectMessages(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage().toLowerCase()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString();
    }
}
