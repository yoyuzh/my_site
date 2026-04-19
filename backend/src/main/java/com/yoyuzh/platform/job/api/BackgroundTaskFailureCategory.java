package com.yoyuzh.platform.job.api;

public enum BackgroundTaskFailureCategory {
    UNSUPPORTED_INPUT(false),
    DATA_STATE(false),
    TRANSIENT_INFRASTRUCTURE(true),
    RATE_LIMITED(true),
    UNKNOWN(true);

    private final boolean retryable;

    BackgroundTaskFailureCategory(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
