package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.api.FileBlobStatus;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.workspace.api.WorkspaceDeferredUploadApi;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class StalePendingBlobCleanupScheduler {

    private final FileBlobRepository fileBlobRepository;
    private final ContentBlobRegistrationApi contentBlobRegistrationApi;
    private final WorkspaceDeferredUploadApi workspaceDeferredUploadApi;
    private final FileContentStorage fileContentStorage;

    public StalePendingBlobCleanupScheduler(FileBlobRepository fileBlobRepository,
                                            ContentBlobRegistrationApi contentBlobRegistrationApi,
                                            WorkspaceDeferredUploadApi workspaceDeferredUploadApi,
                                            FileContentStorage fileContentStorage) {
        this.fileBlobRepository = fileBlobRepository;
        this.contentBlobRegistrationApi = contentBlobRegistrationApi;
        this.workspaceDeferredUploadApi = workspaceDeferredUploadApi;
        this.fileContentStorage = fileContentStorage;
    }

    @Scheduled(fixedRate = 300_000L)
    public void reconcilePendingBlobs() {
        List<FileBlob> staleBlobs = fileBlobRepository.findAllByStatusAndCreatedAtBefore(
                FileBlobStatus.PENDING,
                LocalDateTime.now().minusHours(1)
        );
        for (FileBlob blob : staleBlobs) {
            reconcile(blob);
        }
    }

    private void reconcile(FileBlob blob) {
        if (blob == null || blob.getId() == null) {
            return;
        }
        WorkspaceDeferredUploadApi.TaskStateSnapshot task = workspaceDeferredUploadApi.findTaskState(blob.getUploadTaskId())
                .orElse(null);
        if (task != null && task.status() == BackgroundTaskStatus.COMPLETED && fileContentStorage.blobExists(blob.getObjectKey())) {
            contentBlobRegistrationApi.markBlobReady(blob.getId());
            deleteTempFile(blob.getLocalTempPath());
            return;
        }
        if (task == null
                || task.status() == BackgroundTaskStatus.FAILED
                || task.status() == BackgroundTaskStatus.CANCELLED) {
            contentBlobRegistrationApi.markBlobFailed(blob.getId());
            deleteTempFile(blob.getLocalTempPath());
        }
    }

    private void deleteTempFile(String localTempPath) {
        if (localTempPath == null || localTempPath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(localTempPath));
        } catch (Exception ignored) {
        }
    }
}
