package com.yoyuzh.transfer.internal.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteDownloadTaskTest {

    @Test
    void shouldKeepUpdatedAtStableUntilPersistenceLifecycleRuns() {
        RemoteDownloadTask task = RemoteDownloadTask.createHttp(7L, "/downloads", "https://example.com/demo.zip", "node-1");
        Instant originalUpdatedAt = task.getUpdatedAt();

        task.setStatus(RemoteDownloadStatus.DOWNLOADING);
        task.setDownloaderTaskId("gid-123");
        task.setSelectedFileCount(1);

        assertThat(task.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }
}
