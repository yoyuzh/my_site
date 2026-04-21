package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundTaskStateManagerTest {

    private final BackgroundTaskStateManager stateManager = new BackgroundTaskStateManager(new ObjectMapper());

    @Test
    void shouldBuildCancelledStatePatch() {
        BackgroundTask task = createTask(1, 4);
        LocalDateTime now = LocalDateTime.of(2026, 4, 11, 21, 0, 0);

        Map<String, Object> patch = stateManager.cancelledStatePatch(task, now);

        assertThat(patch.get(BackgroundTaskStateKeys.PHASE)).isEqualTo("cancelled");
        assertThat(patch.get(BackgroundTaskStateKeys.ATTEMPT_COUNT)).isEqualTo(1);
        assertThat(patch.get(BackgroundTaskStateKeys.MAX_ATTEMPTS)).isEqualTo(4);
        assertThat(patch.get(BackgroundTaskStateKeys.HEARTBEAT_AT)).isEqualTo(now.toString());
    }

    @Test
    void shouldBuildCompletedStatePatchAndKeepCanonicalFields() {
        BackgroundTask task = createTask(2, 4);
        LocalDateTime now = LocalDateTime.of(2026, 4, 11, 21, 1, 0);

        Map<String, Object> patch = stateManager.completedStatePatch(
                task,
                now,
                Map.of(
                        "worker", "noop",
                        BackgroundTaskStateKeys.PHASE, "should-be-overwritten"
                )
        );

        assertThat(patch.get("worker")).isEqualTo("noop");
        assertThat(patch.get(BackgroundTaskStateKeys.PHASE)).isEqualTo("completed");
        assertThat(patch.get(BackgroundTaskStateKeys.ATTEMPT_COUNT)).isEqualTo(2);
        assertThat(patch.get(BackgroundTaskStateKeys.MAX_ATTEMPTS)).isEqualTo(4);
        assertThat(patch.get(BackgroundTaskStateKeys.HEARTBEAT_AT)).isEqualTo(now.toString());
    }

    @Test
    void shouldBuildFailedStatePatch() {
        BackgroundTask task = createTask(3, 5);
        LocalDateTime now = LocalDateTime.of(2026, 4, 11, 21, 2, 0);

        Map<String, Object> patch = stateManager.failedStatePatch(
                task,
                "storage timeout",
                BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE,
                now
        );

        assertThat(patch.get(BackgroundTaskStateKeys.PHASE)).isEqualTo("failed");
        assertThat(patch.get(BackgroundTaskStateKeys.ATTEMPT_COUNT)).isEqualTo(3);
        assertThat(patch.get(BackgroundTaskStateKeys.MAX_ATTEMPTS)).isEqualTo(5);
        assertThat(patch.get(BackgroundTaskStateKeys.LAST_FAILURE_MESSAGE)).isEqualTo("storage timeout");
        assertThat(patch.get(BackgroundTaskStateKeys.FAILURE_CATEGORY)).isEqualTo("TRANSIENT_INFRASTRUCTURE");
        assertThat(patch.get(BackgroundTaskStateKeys.HEARTBEAT_AT)).isEqualTo(now.toString());
    }

    @Test
    void shouldBuildRetryQueuedStatePatch() {
        BackgroundTask task = createTask(2, 4);
        LocalDateTime now = LocalDateTime.of(2026, 4, 11, 21, 3, 0);
        LocalDateTime nextRetryAt = now.plusSeconds(30);

        Map<String, Object> patch = stateManager.retryQueuedStatePatch(
                task,
                "temporary unavailable",
                BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE,
                nextRetryAt,
                30,
                now
        );

        assertThat(patch.get(BackgroundTaskStateKeys.PHASE)).isEqualTo("queued");
        assertThat(patch.get(BackgroundTaskStateKeys.ATTEMPT_COUNT)).isEqualTo(2);
        assertThat(patch.get(BackgroundTaskStateKeys.MAX_ATTEMPTS)).isEqualTo(4);
        assertThat(patch.get(BackgroundTaskStateKeys.RETRY_SCHEDULED)).isEqualTo(true);
        assertThat(patch.get(BackgroundTaskStateKeys.NEXT_RETRY_AT)).isEqualTo(nextRetryAt.toString());
        assertThat(patch.get(BackgroundTaskStateKeys.RETRY_DELAY_SECONDS)).isEqualTo(30L);
        assertThat(patch.get(BackgroundTaskStateKeys.LAST_FAILURE_MESSAGE)).isEqualTo("temporary unavailable");
        assertThat(patch.get(BackgroundTaskStateKeys.FAILURE_CATEGORY)).isEqualTo("TRANSIENT_INFRASTRUCTURE");
    }

    private BackgroundTask createTask(int attemptCount, int maxAttempts) {
        BackgroundTask task = new BackgroundTask();
        task.setAttemptCount(attemptCount);
        task.setMaxAttempts(maxAttempts);
        return task;
    }
}
