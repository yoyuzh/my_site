package com.yoyuzh.files.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class UploadSessionCleanupScheduler {

    private final UploadSessionService uploadSessionService;

    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    void pruneExpiredUploadSessions() {
        uploadSessionService.pruneExpiredSessions();
    }
}
