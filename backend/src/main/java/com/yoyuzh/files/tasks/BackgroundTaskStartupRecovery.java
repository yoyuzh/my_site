package com.yoyuzh.files.tasks;

import com.yoyuzh.platform.job.api.BackgroundTaskExecutionGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackgroundTaskStartupRecovery {

    private final BackgroundTaskExecutionGateway backgroundTaskExecutionGateway;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        int recovered = backgroundTaskExecutionGateway.requeueExpiredRunningTasks();
        if (recovered > 0) {
            log.warn("Recovered {} expired RUNNING background task leases back to QUEUED on startup", recovered);
        }
    }
}
