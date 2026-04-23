package com.yoyuzh.files.workspace.internal.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RecycleBinCleanupScheduler {

    private final FileService fileService;

    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    void pruneExpiredRecycleBinItems() {
        fileService.pruneExpiredRecycleBinItems();
    }
}
