package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.platform.job.api.AsyncJobRetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundTaskRetryPolicyTest {

    @Mock
    private AsyncJobRetryPolicy asyncJobRetryPolicy;

    @InjectMocks
    private BackgroundTaskRetryPolicy retryPolicy;

    @Test
    void shouldDelegateMaxAttemptsResolution() {
        when(asyncJobRetryPolicy.resolveMaxAttempts(BackgroundTaskType.ARCHIVE)).thenReturn(4);

        assertThat(retryPolicy.resolveMaxAttempts(BackgroundTaskType.ARCHIVE)).isEqualTo(4);
        verify(asyncJobRetryPolicy).resolveMaxAttempts(BackgroundTaskType.ARCHIVE);
    }

    @Test
    void shouldDelegateRetryDelayResolution() {
        when(asyncJobRetryPolicy.resolveRetryDelaySeconds(
                BackgroundTaskType.ARCHIVE,
                BackgroundTaskFailureCategory.RATE_LIMITED,
                1
        )).thenReturn(120L);

        long delay = retryPolicy.resolveRetryDelaySeconds(
                BackgroundTaskType.ARCHIVE,
                BackgroundTaskFailureCategory.RATE_LIMITED,
                1
        );
        assertThat(delay).isEqualTo(120L);
        verify(asyncJobRetryPolicy).resolveRetryDelaySeconds(
                BackgroundTaskType.ARCHIVE,
                BackgroundTaskFailureCategory.RATE_LIMITED,
                1
        );
    }

    @Test
    void shouldDelegateRemainingAttemptsCheck() {
        BackgroundTask task = new BackgroundTask();
        task.setAttemptCount(1);
        task.setMaxAttempts(2);
        when(asyncJobRetryPolicy.hasRemainingAttempts(1, 2)).thenReturn(true);

        assertThat(retryPolicy.hasRemainingAttempts(task)).isTrue();
        verify(asyncJobRetryPolicy).hasRemainingAttempts(1, 2);
    }
}
