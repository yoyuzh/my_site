package com.yoyuzh.files.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundTaskRetryPolicyTest {

    private final BackgroundTaskRetryPolicy retryPolicy = new BackgroundTaskRetryPolicy();

    @Test
    void shouldResolveConfiguredMaxAttemptsByTaskType() {
        assertThat(retryPolicy.resolveMaxAttempts(BackgroundTaskType.ARCHIVE)).isEqualTo(4);
        assertThat(retryPolicy.resolveMaxAttempts(BackgroundTaskType.EXTRACT)).isEqualTo(3);
        assertThat(retryPolicy.resolveMaxAttempts(BackgroundTaskType.MEDIA_META)).isEqualTo(2);
    }

    @Test
    void shouldUseLongerBackoffForRateLimitedFailures() {
        long delay = retryPolicy.resolveRetryDelaySeconds(
                BackgroundTaskType.ARCHIVE,
                BackgroundTaskFailureCategory.RATE_LIMITED,
                1
        );

        assertThat(delay).isEqualTo(120L);
    }

    @Test
    void shouldCapBackoffGrowthForUnknownFailures() {
        long delay = retryPolicy.resolveRetryDelaySeconds(
                BackgroundTaskType.MEDIA_META,
                BackgroundTaskFailureCategory.UNKNOWN,
                5
        );

        assertThat(delay).isEqualTo(120L);
    }
}
