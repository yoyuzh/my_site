package com.yoyuzh.platform.job.internal.application;

public final class BackgroundTaskStateKeys {

    public static final String PHASE = "phase";
    public static final String ATTEMPT_COUNT = "attemptCount";
    public static final String MAX_ATTEMPTS = "maxAttempts";
    public static final String RETRY_SCHEDULED = "retryScheduled";
    public static final String NEXT_RETRY_AT = "nextRetryAt";
    public static final String RETRY_DELAY_SECONDS = "retryDelaySeconds";
    public static final String LAST_FAILURE_MESSAGE = "lastFailureMessage";
    public static final String LAST_FAILURE_AT = "lastFailureAt";
    public static final String FAILURE_CATEGORY = "failureCategory";
    public static final String WORKER_OWNER = "workerOwner";
    public static final String HEARTBEAT_AT = "heartbeatAt";
    public static final String LEASE_EXPIRES_AT = "leaseExpiresAt";
    public static final String STARTED_AT = "startedAt";

    private BackgroundTaskStateKeys() {
    }
}
