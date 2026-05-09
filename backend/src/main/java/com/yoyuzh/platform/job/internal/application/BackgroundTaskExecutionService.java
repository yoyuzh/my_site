package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.infra.BackgroundTaskRepository;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BackgroundTaskExecutionService {

    private static final List<String> RETRY_TRANSIENT_STATE_KEYS = List.of(
            BackgroundTaskStateKeys.RETRY_SCHEDULED,
            BackgroundTaskStateKeys.NEXT_RETRY_AT,
            BackgroundTaskStateKeys.RETRY_DELAY_SECONDS,
            BackgroundTaskStateKeys.LAST_FAILURE_MESSAGE,
            BackgroundTaskStateKeys.LAST_FAILURE_AT,
            BackgroundTaskStateKeys.FAILURE_CATEGORY
    );
    private static final List<String> RUNNING_TRANSIENT_STATE_KEYS = List.of(
            BackgroundTaskStateKeys.WORKER_OWNER,
            BackgroundTaskStateKeys.LEASE_EXPIRES_AT
    );
    private static final int EXPIRED_RUNNING_TASK_BATCH_SIZE = 100;

    private final BackgroundTaskRepository backgroundTaskRepository;
    private final BackgroundTaskRetryPolicy retryPolicy;
    private final BackgroundTaskStateManager stateManager;

    @Transactional
    public int requeueExpiredRunningTasks() {
        LocalDateTime now = LocalDateTime.now();
        int recovered = 0;
        for (Long taskId : backgroundTaskRepository.findExpiredRunningTaskIds(
                BackgroundTaskStatus.RUNNING,
                now,
                PageRequest.of(0, EXPIRED_RUNNING_TASK_BATCH_SIZE)
        )) {
            int requeued = backgroundTaskRepository.requeueExpiredRunningTask(
                    taskId,
                    BackgroundTaskStatus.RUNNING,
                    BackgroundTaskStatus.QUEUED,
                    now,
                    now
            );
            if (requeued != 1) {
                continue;
            }
            BackgroundTask task = backgroundTaskRepository.findById(taskId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "task not found"));
            resetTaskToQueued(task);
            backgroundTaskRepository.save(task);
            recovered += 1;
        }
        return recovered;
    }

    public List<Long> findQueuedTaskIds(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return backgroundTaskRepository.findReadyTaskIdsByStatusOrder(
                BackgroundTaskStatus.QUEUED,
                LocalDateTime.now(),
                PageRequest.of(0, limit)
        );
    }

    public List<Long> findQueuedTaskIdsByTypes(Collection<BackgroundTaskType> types, int limit) {
        if (limit <= 0 || types == null || types.isEmpty()) {
            return List.of();
        }
        return backgroundTaskRepository.findReadyTaskIdsByStatusAndTypeInOrder(
                BackgroundTaskStatus.QUEUED,
                List.copyOf(types),
                LocalDateTime.now(),
                PageRequest.of(0, limit)
        );
    }

    @Transactional
    public Optional<BackgroundTask> claimQueuedTask(Long id, String workerOwner, long leaseDurationSeconds) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiresAt = now.plusSeconds(Math.max(30L, leaseDurationSeconds));
        int claimed = backgroundTaskRepository.claimQueuedTask(
                id,
                BackgroundTaskStatus.QUEUED,
                BackgroundTaskStatus.RUNNING,
                workerOwner,
                leaseExpiresAt,
                now,
                now
        );
        if (claimed != 1) {
            return Optional.empty();
        }
        Optional<BackgroundTask> task = backgroundTaskRepository.findById(id);
        task.ifPresent(claimedTask -> {
            claimedTask.setLeaseOwner(workerOwner);
            claimedTask.setLeaseExpiresAt(leaseExpiresAt);
            claimedTask.setHeartbeatAt(now);
            claimedTask.setPublicStateJson(stateManager.merge(
                    claimedTask.getPublicStateJson(),
                    stateManager.runningStatePatch(claimedTask, workerOwner, now, leaseExpiresAt, true),
                    RETRY_TRANSIENT_STATE_KEYS
            ));
        });
        task.ifPresent(backgroundTaskRepository::save);
        return task;
    }

    @Transactional
    public BackgroundTask markWorkerTaskProgress(Long id,
                                                 String workerOwner,
                                                 Map<String, Object> publicStatePatch,
                                                 long leaseDurationSeconds) {
        LeaseTouch leaseTouch = refreshLease(id, workerOwner, leaseDurationSeconds);
        BackgroundTask task = backgroundTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "task not found"));
        task.setLeaseOwner(workerOwner);
        task.setLeaseExpiresAt(leaseTouch.leaseExpiresAt());
        task.setHeartbeatAt(leaseTouch.now());
        Map<String, Object> nextPatch = new LinkedHashMap<>(stateManager.runningStatePatch(
                task,
                workerOwner,
                leaseTouch.now(),
                leaseTouch.leaseExpiresAt(),
                false
        ));
        if (publicStatePatch != null) {
            nextPatch.putAll(publicStatePatch);
        }
        task.setPublicStateJson(stateManager.merge(task.getPublicStateJson(), nextPatch));
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markWorkerTaskCompleted(Long id,
                                                  String workerOwner,
                                                  Map<String, Object> publicStatePatch,
                                                  long leaseDurationSeconds) {
        LeaseTouch leaseTouch = refreshLease(id, workerOwner, leaseDurationSeconds);
        BackgroundTask task = backgroundTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "task not found"));
        task.setPublicStateJson(stateManager.merge(
                task.getPublicStateJson(),
                stateManager.completedStatePatch(task, leaseTouch.now(), publicStatePatch),
                stateManager.removableKeys(RETRY_TRANSIENT_STATE_KEYS, RUNNING_TRANSIENT_STATE_KEYS)
        ));
        task.setStatus(BackgroundTaskStatus.COMPLETED);
        task.setNextRunAt(null);
        clearLease(task);
        task.setFinishedAt(leaseTouch.now());
        task.setErrorMessage(null);
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markWorkerTaskRequeued(Long id,
                                                 String workerOwner,
                                                 Map<String, Object> publicStatePatch,
                                                 long nextRunDelaySeconds,
                                                 long leaseDurationSeconds) {
        LeaseTouch leaseTouch = refreshLease(id, workerOwner, leaseDurationSeconds);
        BackgroundTask task = backgroundTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "task not found"));
        LocalDateTime nextRunAt = leaseTouch.now().plusSeconds(Math.max(1L, nextRunDelaySeconds));
        task.setPublicStateJson(stateManager.merge(
                task.getPublicStateJson(),
                requeuedStatePatch(task, leaseTouch.now(), nextRunAt, publicStatePatch),
                RUNNING_TRANSIENT_STATE_KEYS
        ));
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setNextRunAt(nextRunAt);
        clearLease(task);
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        return backgroundTaskRepository.save(task);
    }

    @Transactional
    public BackgroundTask markWorkerTaskFailed(Long id,
                                               String workerOwner,
                                               String errorMessage,
                                               BackgroundTaskFailureCategory failureCategory,
                                               long leaseDurationSeconds) {
        LeaseTouch leaseTouch = refreshLease(id, workerOwner, leaseDurationSeconds);
        BackgroundTask task = backgroundTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "task not found"));
        String normalizedErrorMessage = StringUtils.hasText(errorMessage) ? errorMessage.trim() : "task failed";
        LocalDateTime now = leaseTouch.now();
        if (failureCategory.isRetryable() && retryPolicy.hasRemainingAttempts(task)) {
            long retryDelaySeconds = retryPolicy.resolveRetryDelaySeconds(task.getType(), failureCategory, task.getAttemptCount());
            LocalDateTime nextRunAt = now.plusSeconds(retryDelaySeconds);
            task.setStatus(BackgroundTaskStatus.QUEUED);
            task.setNextRunAt(nextRunAt);
            clearLease(task);
            task.setFinishedAt(null);
            task.setErrorMessage(null);
            task.setPublicStateJson(stateManager.merge(
                    task.getPublicStateJson(),
                    stateManager.retryQueuedStatePatch(
                            task,
                            normalizedErrorMessage,
                            failureCategory,
                            nextRunAt,
                            retryDelaySeconds,
                            now
                    ),
                    RUNNING_TRANSIENT_STATE_KEYS
            ));
            return backgroundTaskRepository.save(task);
        }

        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(stateManager.merge(
                task.getPublicStateJson(),
                stateManager.failedStatePatch(task, normalizedErrorMessage, failureCategory, now),
                stateManager.removableKeys(
                        List.of(
                                BackgroundTaskStateKeys.RETRY_SCHEDULED,
                                BackgroundTaskStateKeys.NEXT_RETRY_AT,
                                BackgroundTaskStateKeys.RETRY_DELAY_SECONDS
                        ),
                        RUNNING_TRANSIENT_STATE_KEYS
                )
        ));
        task.setStatus(BackgroundTaskStatus.FAILED);
        task.setFinishedAt(now);
        task.setErrorMessage(normalizedErrorMessage);
        return backgroundTaskRepository.save(task);
    }

    private void resetTaskToQueued(BackgroundTask task) {
        task.setNextRunAt(null);
        clearLease(task);
        task.setPublicStateJson(stateManager.resetPublicStateForRetry(
                task.getPublicStateJson(),
                task.getPrivateStateJson(),
                task.getAttemptCount(),
                task.getMaxAttempts()
        ));
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setFinishedAt(null);
        task.setErrorMessage(null);
    }

    private LeaseTouch refreshLease(Long id, String workerOwner, long leaseDurationSeconds) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiresAt = now.plusSeconds(Math.max(30L, leaseDurationSeconds));
        int refreshed = backgroundTaskRepository.refreshRunningTaskLease(
                id,
                BackgroundTaskStatus.RUNNING,
                workerOwner,
                leaseExpiresAt,
                now,
                now
        );
        if (refreshed != 1) {
            throw new BackgroundTaskLeaseLostException(id, workerOwner);
        }
        return new LeaseTouch(now, leaseExpiresAt);
    }

    private void clearLease(BackgroundTask task) {
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setHeartbeatAt(null);
    }

    private Map<String, Object> requeuedStatePatch(BackgroundTask task,
                                                   LocalDateTime heartbeatAt,
                                                   LocalDateTime nextRunAt,
                                                   Map<String, Object> publicStatePatch) {
        Map<String, Object> patch = new LinkedHashMap<>(stateManager.retryStatePatch(task.getAttemptCount(), task.getMaxAttempts()));
        patch.put(BackgroundTaskStateKeys.HEARTBEAT_AT, heartbeatAt.toString());
        patch.put("nextRunAt", nextRunAt.toString());
        if (publicStatePatch != null) {
            patch.putAll(publicStatePatch);
        }
        return patch;
    }

    private record LeaseTouch(LocalDateTime now, LocalDateTime leaseExpiresAt) {
    }
}
