package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.transfer.api.RemoteDownloadExecutionApi;
import com.yoyuzh.transfer.api.RemoteDownloadExecutionResult;
import com.yoyuzh.transfer.internal.domain.DownloadEngineType;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadCandidateFile;
import com.yoyuzh.transfer.api.RemoteDownloadSourceType;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadStatus;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import com.yoyuzh.transfer.internal.infra.Aria2Client;
import com.yoyuzh.transfer.internal.infra.QbittorrentClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RuntimeRemoteDownloadExecutionApi implements RemoteDownloadExecutionApi {

    private static final long DEFAULT_POLL_DELAY_SECONDS = 15L;

    private final RemoteDownloadService remoteDownloadService;
    private final Aria2Client aria2Client;
    private final QbittorrentClient qbittorrentClient;
    private final RemoteDownloadImportService remoteDownloadImportService;

    public RuntimeRemoteDownloadExecutionApi(RemoteDownloadService remoteDownloadService,
                                             Aria2Client aria2Client,
                                             QbittorrentClient qbittorrentClient,
                                             RemoteDownloadImportService remoteDownloadImportService) {
        this.remoteDownloadService = remoteDownloadService;
        this.aria2Client = aria2Client;
        this.qbittorrentClient = qbittorrentClient;
        this.remoteDownloadImportService = remoteDownloadImportService;
    }

    @Override
    @Transactional
    public RemoteDownloadExecutionResult start(Long remoteDownloadId) {
        RemoteDownloadTask task = remoteDownloadService.loadForExecution(remoteDownloadId);
        if (task.getDownloaderTaskId() != null && !task.getDownloaderTaskId().isBlank()) {
            return advanceSubmittedTask(task);
        }
        String downloaderTaskId;
        String phase;
        if (task.getEngineType() == DownloadEngineType.ARIA2) {
            downloaderTaskId = aria2Client.submitHttp(task.getSourceValue(), task.getDownloadNodeId());
            phase = "downloading";
        } else if (task.getSourceType() == RemoteDownloadSourceType.MAGNET) {
            downloaderTaskId = qbittorrentClient.submitMagnet(task.getSourceValue(), task.getDownloadNodeId());
            phase = "fetching-metadata";
        } else {
            downloaderTaskId = qbittorrentClient.submitTorrent(
                    task.getSourceValue(),
                    task.getSourceContent(),
                    task.getDownloadNodeId()
            );
            phase = "fetching-metadata";
        }
        remoteDownloadService.markSubmitted(remoteDownloadId, downloaderTaskId, phaseToStatus(phase));
        return new RemoteDownloadExecutionResult(
                remoteDownloadId,
                task.getEngineType().name(),
                phase,
                downloaderTaskId,
                false,
                DEFAULT_POLL_DELAY_SECONDS
        );
    }

    private RemoteDownloadExecutionResult advanceSubmittedTask(RemoteDownloadTask task) {
        if (task.getEngineType() == DownloadEngineType.ARIA2) {
            return advanceAria2Task(task);
        }
        return advanceQbittorrentTask(task);
    }

    private RemoteDownloadExecutionResult advanceAria2Task(RemoteDownloadTask task) {
        Aria2Client.TaskStatus status = aria2Client.queryStatus(task.getDownloaderTaskId());
        if ("complete".equalsIgnoreCase(status.status())) {
            return completeAfterImport(task, status.outputPath(), null, false);
        }
        if ("error".equalsIgnoreCase(status.status())) {
            remoteDownloadService.markFailed(task.getId(), status.errorCode(), status.errorMessage());
            return new RemoteDownloadExecutionResult(
                    task.getId(),
                    task.getEngineType().name(),
                    "failed",
                    task.getDownloaderTaskId(),
                    true,
                    null
            );
        }
        remoteDownloadService.markDownloading(task.getId());
        return new RemoteDownloadExecutionResult(
                task.getId(),
                task.getEngineType().name(),
                "downloading",
                task.getDownloaderTaskId(),
                false,
                DEFAULT_POLL_DELAY_SECONDS
        );
    }

    private RemoteDownloadExecutionResult advanceQbittorrentTask(RemoteDownloadTask task) {
        QbittorrentClient.TorrentStatus torrent = qbittorrentClient.queryTorrent(task.getDownloaderTaskId());
        if (isQbFailedState(torrent.state())) {
            remoteDownloadService.markFailed(task.getId(), torrent.state(), "qBittorrent task failed");
            return new RemoteDownloadExecutionResult(
                    task.getId(),
                    task.getEngineType().name(),
                    "failed",
                    task.getDownloaderTaskId(),
                    true,
                    null
            );
        }
        if (task.getStatus() == RemoteDownloadStatus.AWAITING_FILE_SELECTION && task.getSelectedFileCount() == 0) {
            return new RemoteDownloadExecutionResult(
                    task.getId(),
                    task.getEngineType().name(),
                    "awaiting-file-selection",
                    task.getDownloaderTaskId(),
                    false,
                    DEFAULT_POLL_DELAY_SECONDS
            );
        }
        if (isQbMetadataState(torrent.state()) || task.getStatus() == RemoteDownloadStatus.FETCHING_METADATA) {
            List<QbittorrentClient.TorrentFile> files = qbittorrentClient.listFiles(task.getDownloaderTaskId());
            if (files.isEmpty()) {
                remoteDownloadService.markFetchingMetadata(task.getId());
                return new RemoteDownloadExecutionResult(
                        task.getId(),
                        task.getEngineType().name(),
                        "fetching-metadata",
                        task.getDownloaderTaskId(),
                        false,
                        DEFAULT_POLL_DELAY_SECONDS
                );
            }
            remoteDownloadService.markAwaitingFileSelection(task.getId(), files.stream()
                    .map(this::toCandidateFile)
                    .toList());
            return new RemoteDownloadExecutionResult(
                    task.getId(),
                    task.getEngineType().name(),
                    "awaiting-file-selection",
                    task.getDownloaderTaskId(),
                    false,
                    DEFAULT_POLL_DELAY_SECONDS
            );
        }
        if (torrent.progress() >= 1.0d || isQbCompletedState(torrent.state())) {
            return completeAfterImport(task, torrent.contentPath(), torrent.savePath(), true);
        }
        remoteDownloadService.markDownloading(task.getId());
        return new RemoteDownloadExecutionResult(
                task.getId(),
                task.getEngineType().name(),
                "downloading",
                task.getDownloaderTaskId(),
                false,
                DEFAULT_POLL_DELAY_SECONDS
        );
    }

    private RemoteDownloadCandidateFile toCandidateFile(QbittorrentClient.TorrentFile file) {
        RemoteDownloadCandidateFile candidateFile = new RemoteDownloadCandidateFile();
        candidateFile.setFileKey(file.fileKey());
        candidateFile.setRelativePath(file.relativePath());
        candidateFile.setSize(file.size());
        return candidateFile;
    }

    private boolean isQbMetadataState(String state) {
        return "metaDL".equalsIgnoreCase(state)
                || "checkingResumeData".equalsIgnoreCase(state);
    }

    private boolean isQbCompletedState(String state) {
        return "uploading".equalsIgnoreCase(state)
                || "stalledUP".equalsIgnoreCase(state)
                || "pausedUP".equalsIgnoreCase(state)
                || "queuedUP".equalsIgnoreCase(state)
                || "forcedUP".equalsIgnoreCase(state);
    }

    private boolean isQbFailedState(String state) {
        return "error".equalsIgnoreCase(state)
                || "missingFiles".equalsIgnoreCase(state)
                || "unknown".equalsIgnoreCase(state);
    }

    private RemoteDownloadExecutionResult completeAfterImport(RemoteDownloadTask task,
                                                              String outputPath,
                                                              String savePath,
                                                              boolean cleanupDownloaderTask) {
        try {
            remoteDownloadService.markImporting(task.getId());
            int importedFileCount = remoteDownloadImportService.importCompletedDownload(task, outputPath, savePath);
            if (cleanupDownloaderTask) {
                qbittorrentClient.delete(task.getDownloaderTaskId(), true);
            }
            remoteDownloadService.markCompleted(task.getId(), importedFileCount);
            return new RemoteDownloadExecutionResult(
                    task.getId(),
                    task.getEngineType().name(),
                    "completed",
                    task.getDownloaderTaskId(),
                    true,
                    null
            );
        } catch (RuntimeException ex) {
            remoteDownloadService.markFailed(task.getId(), "remote-download-import-failed", failureMessage(ex));
            throw ex;
        }
    }

    private String failureMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }

    private RemoteDownloadStatus phaseToStatus(String phase) {
        if ("downloading".equals(phase)) {
            return RemoteDownloadStatus.DOWNLOADING;
        }
        return RemoteDownloadStatus.FETCHING_METADATA;
    }
}
