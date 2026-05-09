package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.shared.kernel.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BackgroundTaskWorker {

    private static final int DEFAULT_BATCH_SIZE = 5;
    private static final int DEFAULT_LIGHTWEIGHT_BATCH_SIZE = 1;
    private static final long DEFAULT_LEASE_DURATION_SECONDS = 120L;
    private static final Set<BackgroundTaskType> LIGHTWEIGHT_TASK_TYPES = Set.of(BackgroundTaskType.WORKSPACE_MUTATION);

    private final BackgroundTaskExecutionGateway backgroundTaskExecutionGateway;
    private final List<BackgroundTaskHandler> handlers;
    private final String workerOwner;
    private final Executor lightweightTaskExecutor;
    private final ExecutorService managedLightweightTaskExecutor;
    private final int lightweightConcurrency;
    private final boolean lightweightWakeupEnabled;

    @Autowired
    public BackgroundTaskWorker(BackgroundTaskExecutionGateway backgroundTaskExecutionGateway,
                                List<BackgroundTaskHandler> handlers,
                                @Value("${app.background-tasks.worker.lightweight-concurrency:4}") int lightweightConcurrency,
                                @Value("${app.background-tasks.worker.lightweight-wakeup-enabled:true}") boolean lightweightWakeupEnabled) {
        this(
                backgroundTaskExecutionGateway,
                handlers,
                createLightweightTaskExecutor(Math.max(1, lightweightConcurrency)),
                Math.max(1, lightweightConcurrency),
                lightweightWakeupEnabled
        );
    }

    BackgroundTaskWorker(BackgroundTaskExecutionGateway backgroundTaskExecutionGateway,
                         List<BackgroundTaskHandler> handlers) {
        this(backgroundTaskExecutionGateway, handlers, Runnable::run, 1, true);
    }

    BackgroundTaskWorker(BackgroundTaskExecutionGateway backgroundTaskExecutionGateway,
                         List<BackgroundTaskHandler> handlers,
                         Executor lightweightTaskExecutor,
                         int lightweightConcurrency,
                         boolean lightweightWakeupEnabled) {
        this.backgroundTaskExecutionGateway = backgroundTaskExecutionGateway;
        this.handlers = List.copyOf(handlers);
        this.workerOwner = UUID.randomUUID().toString().replace("-", "");
        this.lightweightTaskExecutor = lightweightTaskExecutor;
        this.managedLightweightTaskExecutor = lightweightTaskExecutor instanceof ExecutorService executorService
                ? executorService
                : null;
        this.lightweightConcurrency = Math.max(1, lightweightConcurrency);
        this.lightweightWakeupEnabled = lightweightWakeupEnabled;
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
        return processReadyTaskIds(backgroundTaskExecutionGateway.findQueuedTaskIds(maxTasks));
    }

    public int processQueuedTasksByTypes(Collection<BackgroundTaskType> types, int maxTasks) {
        backgroundTaskExecutionGateway.requeueExpiredRunningTasks();
        return processReadyTaskIds(backgroundTaskExecutionGateway.findQueuedTaskIdsByTypes(types, maxTasks));
    }

    public void wakeLightweightTasks() {
        if (!lightweightWakeupEnabled) {
            return;
        }
        for (int i = 0; i < lightweightConcurrency; i += 1) {
            lightweightTaskExecutor.execute(() -> processQueuedTasksByTypes(LIGHTWEIGHT_TASK_TYPES, DEFAULT_LIGHTWEIGHT_BATCH_SIZE));
        }
    }

    @PreDestroy
    public void shutdown() {
        if (managedLightweightTaskExecutor != null) {
            managedLightweightTaskExecutor.shutdown();
        }
    }

    private int processReadyTaskIds(List<Long> taskIds) {
        int processedCount = 0;
        for (Long taskId : taskIds) {
            var claimedTask = backgroundTaskExecutionGateway.claimQueuedTask(taskId, workerOwner, DEFAULT_LEASE_DURATION_SECONDS);
            if (claimedTask.isEmpty()) {
                continue;
            }

            execute(claimedTask.get());
            processedCount += 1;
        }
        return processedCount;
    }

    private static ExecutorService createLightweightTaskExecutor(int poolSize) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "workspace-mutation-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(poolSize, threadFactory);
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
            if (result.completed()) {
                backgroundTaskExecutionGateway.markWorkerTaskCompleted(
                        task.getId(),
                        workerOwner,
                        result.publicStatePatch(),
                        DEFAULT_LEASE_DURATION_SECONDS
                );
            } else {
                backgroundTaskExecutionGateway.markWorkerTaskRequeued(
                        task.getId(),
                        workerOwner,
                        result.publicStatePatch(),
                        result.nextRunDelaySeconds() == null ? 1L : result.nextRunDelaySeconds(),
                        DEFAULT_LEASE_DURATION_SECONDS
                );
            }
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
        if (type == BackgroundTaskType.ARCHIVE) {
            return "archiving";
        }
        if (type == BackgroundTaskType.EXTRACT) {
            return "extracting";
        }
        if (type == BackgroundTaskType.MEDIA_META) {
            return "extracting-metadata";
        }
        if (type == BackgroundTaskType.WORKSPACE_MUTATION) {
            return "mutating-workspace";
        }
        if (type == BackgroundTaskType.SEARCH_INDEX_REBUILD) {
            return "rebuilding-search-index";
        }
        if (type == BackgroundTaskType.STORAGE_POLICY_MIGRATION) {
            return "planning-storage-policy-migration";
        }
        return "running";
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
