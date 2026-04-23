package com.yoyuzh.platform.job.internal.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BackgroundTaskStartupRecovery {

    private static final Logger log = LoggerFactory.getLogger(BackgroundTaskStartupRecovery.class);

    private final BackgroundTaskExecutionGateway backgroundTaskExecutionGateway;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        int recovered = backgroundTaskExecutionGateway.requeueExpiredRunningTasks();
        if (recovered > 0) {
            log.warn("Recovered {} expired RUNNING background task leases back to QUEUED on startup", recovered);
        }
    }
}
