package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.transfer.api.CreateRemoteDownloadCommand;
import com.yoyuzh.transfer.api.RemoteDownloadApi;
import com.yoyuzh.transfer.api.RemoteDownloadDetailResponse;
import com.yoyuzh.transfer.api.RemoteDownloadListItemResponse;
import com.yoyuzh.transfer.api.SelectRemoteDownloadFilesCommand;
import com.yoyuzh.transfer.internal.domain.DownloadEngineType;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadCandidateFile;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadStatus;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import com.yoyuzh.transfer.internal.infra.Aria2Client;
import com.yoyuzh.transfer.internal.infra.QbittorrentClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuntimeRemoteDownloadApi implements RemoteDownloadApi {

    private final RemoteDownloadService remoteDownloadService;
    private final Aria2Client aria2Client;
    private final QbittorrentClient qbittorrentClient;

    public RuntimeRemoteDownloadApi(RemoteDownloadService remoteDownloadService,
                                    Aria2Client aria2Client,
                                    QbittorrentClient qbittorrentClient) {
        this.remoteDownloadService = remoteDownloadService;
        this.aria2Client = aria2Client;
        this.qbittorrentClient = qbittorrentClient;
    }

    @Override
    public RemoteDownloadDetailResponse create(Long userId, CreateRemoteDownloadCommand command) {
        return remoteDownloadService.create(userId, command);
    }

    @Override
    public List<RemoteDownloadListItemResponse> listOwned(Long userId) {
        return remoteDownloadService.listOwned(userId);
    }

    @Override
    public RemoteDownloadDetailResponse getOwned(Long userId, Long id) {
        return remoteDownloadService.getOwned(userId, id);
    }

    @Override
    public RemoteDownloadDetailResponse retry(Long userId, Long id) {
        return remoteDownloadService.retry(userId, id);
    }

    @Override
    public RemoteDownloadDetailResponse selectFiles(Long userId, Long id, SelectRemoteDownloadFilesCommand command) {
        RemoteDownloadTask task = remoteDownloadService.loadOwnedTask(userId, id);
        if (task.getEngineType() == DownloadEngineType.QBITTORRENT && hasDownloaderTaskId(task)) {
            List<String> selectedFileKeys = command.fileKeys() == null ? List.of() : command.fileKeys();
            if (selectedFileKeys.isEmpty()) {
                qbittorrentClient.delete(task.getDownloaderTaskId(), true);
                return remoteDownloadService.cancel(userId, id);
            }
            List<String> allFileKeys = task.getCandidateFiles().stream()
                    .map(RemoteDownloadCandidateFile::getFileKey)
                    .toList();
            List<String> unselectedFileKeys = allFileKeys.stream()
                    .filter(fileKey -> !selectedFileKeys.contains(fileKey))
                    .toList();
            qbittorrentClient.updateFileSelection(task.getDownloaderTaskId(), selectedFileKeys, unselectedFileKeys);
        }
        return remoteDownloadService.selectFiles(userId, id, command);
    }

    @Override
    public RemoteDownloadDetailResponse cancel(Long userId, Long id) {
        RemoteDownloadTask task = remoteDownloadService.loadOwnedTask(userId, id);
        if (!isTerminal(task.getStatus()) && hasDownloaderTaskId(task)) {
            if (task.getEngineType() == DownloadEngineType.ARIA2) {
                aria2Client.cancel(task.getDownloaderTaskId());
            } else {
                qbittorrentClient.delete(task.getDownloaderTaskId(), true);
            }
        }
        return remoteDownloadService.cancel(userId, id);
    }

    private boolean hasDownloaderTaskId(RemoteDownloadTask task) {
        return task.getDownloaderTaskId() != null && !task.getDownloaderTaskId().isBlank();
    }

    private boolean isTerminal(RemoteDownloadStatus status) {
        return status == RemoteDownloadStatus.COMPLETED
                || status == RemoteDownloadStatus.FAILED
                || status == RemoteDownloadStatus.CANCELED;
    }
}
