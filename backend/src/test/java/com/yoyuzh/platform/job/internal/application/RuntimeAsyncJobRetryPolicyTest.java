package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeAsyncJobRetryPolicyTest {

    private final RuntimeAsyncJobRetryPolicy retryPolicy = new RuntimeAsyncJobRetryPolicy();

    @Test
    void shouldResolveConfiguredMaxAttemptsByTaskType() {
        assertThat(retryPolicy.resolveMaxAttempts(BackgroundTaskType.ARCHIVE)).isEqualTo(4);
        assertThat(retryPolicy.resolveMaxAttempts(BackgroundTaskType.EXTRACT)).isEqualTo(3);
        assertThat(retryPolicy.resolveMaxAttempts(BackgroundTaskType.MEDIA_META)).isEqualTo(2);
        assertThat(retryPolicy.resolveMaxAttempts(BackgroundTaskType.BLOB_UPLOAD)).isEqualTo(3);
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

    @Test
    void shouldDetectRemainingAttempts() {
        assertThat(retryPolicy.hasRemainingAttempts(1, 2)).isTrue();
    }
}
