package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.api.CreateRemoteDownloadCommand;
import com.yoyuzh.transfer.api.RemoteDownloadApi;
import com.yoyuzh.transfer.api.RemoteDownloadCandidateFileResponse;
import com.yoyuzh.transfer.api.RemoteDownloadDetailResponse;
import com.yoyuzh.transfer.api.RemoteDownloadListItemResponse;
import com.yoyuzh.transfer.api.SelectRemoteDownloadFilesCommand;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadCandidateFile;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadSourceType;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadStatus;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import com.yoyuzh.transfer.internal.infra.RemoteDownloadTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RemoteDownloadService {

    private final RemoteDownloadTaskRepository remoteDownloadTaskRepository;
    private final BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;

    public RemoteDownloadService(RemoteDownloadTaskRepository remoteDownloadTaskRepository,
                                 BackgroundTaskLifecycleApi backgroundTaskLifecycleApi) {
        this.remoteDownloadTaskRepository = remoteDownloadTaskRepository;
        this.backgroundTaskLifecycleApi = backgroundTaskLifecycleApi;
    }

    @Transactional
    public RemoteDownloadDetailResponse create(Long userId, CreateRemoteDownloadCommand command) {
        RemoteDownloadTask task = buildTask(userId, command);
        RemoteDownloadTask savedTask = remoteDownloadTaskRepository.save(task);

        BackgroundTaskView backgroundTask = backgroundTaskLifecycleApi.createQueuedTaskByUserId(
                userId,
                BackgroundTaskType.REMOTE_DOWNLOAD,
                initialPublicState(savedTask),
                initialPrivateState(savedTask),
                correlationId(savedTask)
        );
        savedTask.setBackgroundTaskId(backgroundTask.id());
        return toDetailResponse(remoteDownloadTaskRepository.save(savedTask));
    }

    @Transactional(readOnly = true)
    public List<RemoteDownloadListItemResponse> listOwned(Long userId) {
        return remoteDownloadTaskRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toListItemResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RemoteDownloadDetailResponse getOwned(Long userId, Long id) {
        return toDetailResponse(requireOwnedTask(userId, id));
    }

    @Transactional
    public RemoteDownloadDetailResponse selectFiles(Long userId, Long id, SelectRemoteDownloadFilesCommand command) {
        RemoteDownloadTask task = requireOwnedTask(userId, id);
        requireStatus(task, EnumSet.of(RemoteDownloadStatus.AWAITING_FILE_SELECTION), "remote download is not awaiting file selection");
        int selectedCount = 0;
        for (RemoteDownloadCandidateFile candidateFile : task.getCandidateFiles()) {
            boolean selected = command.fileKeys() != null && command.fileKeys().contains(candidateFile.getFileKey());
            candidateFile.setSelected(selected);
            if (selected) {
                selectedCount += 1;
            }
        }
        task.setSelectedFileCount(selectedCount);
        if (task.getStatus() == RemoteDownloadStatus.AWAITING_FILE_SELECTION && selectedCount > 0) {
            task.setStatus(RemoteDownloadStatus.SUBMITTED);
        }
        return toDetailResponse(remoteDownloadTaskRepository.save(task));
    }

    @Transactional
    public RemoteDownloadDetailResponse cancel(Long userId, Long id) {
        RemoteDownloadTask task = requireOwnedTask(userId, id);
        requireStatus(task, nonTerminalStatuses(), "remote download is already finished");
        task.setStatus(RemoteDownloadStatus.CANCELED);
        task.setFinishedAt(Instant.now());
        return toDetailResponse(remoteDownloadTaskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public RemoteDownloadTask loadForExecution(Long remoteDownloadId) {
        return remoteDownloadTaskRepository.findById(remoteDownloadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "remote download task not found"));
    }

    @Transactional(readOnly = true)
    public RemoteDownloadTask loadOwnedTask(Long userId, Long id) {
        return requireOwnedTask(userId, id);
    }

    @Transactional
    public void markSubmitted(Long remoteDownloadId, String downloaderTaskId, RemoteDownloadStatus status) {
        RemoteDownloadTask task = remoteDownloadTaskRepository.findById(remoteDownloadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "remote download task not found"));
        requireStatus(task, EnumSet.of(RemoteDownloadStatus.PENDING), "remote download cannot be submitted from current status");
        task.setDownloaderTaskId(downloaderTaskId);
        task.setStatus(status);
        task.setFailureCode(null);
        task.setFailureMessage(null);
        task.setFinishedAt(null);
        remoteDownloadTaskRepository.save(task);
    }

    @Transactional
    public void markDownloading(Long remoteDownloadId) {
        RemoteDownloadTask task = remoteDownloadTaskRepository.findById(remoteDownloadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "remote download task not found"));
        requireStatus(task, EnumSet.of(
                RemoteDownloadStatus.SUBMITTED,
                RemoteDownloadStatus.FETCHING_METADATA,
                RemoteDownloadStatus.AWAITING_FILE_SELECTION,
                RemoteDownloadStatus.DOWNLOADING
        ), "remote download cannot enter downloading from current status");
        task.setStatus(RemoteDownloadStatus.DOWNLOADING);
        remoteDownloadTaskRepository.save(task);
    }

    @Transactional
    public void markFetchingMetadata(Long remoteDownloadId) {
        RemoteDownloadTask task = remoteDownloadTaskRepository.findById(remoteDownloadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "remote download task not found"));
        requireStatus(task, EnumSet.of(RemoteDownloadStatus.FETCHING_METADATA, RemoteDownloadStatus.SUBMITTED), "remote download cannot fetch metadata from current status");
        task.setStatus(RemoteDownloadStatus.FETCHING_METADATA);
        remoteDownloadTaskRepository.save(task);
    }

    @Transactional
    public void markAwaitingFileSelection(Long remoteDownloadId, List<RemoteDownloadCandidateFile> candidateFiles) {
        RemoteDownloadTask task = remoteDownloadTaskRepository.findById(remoteDownloadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "remote download task not found"));
        requireStatus(task, EnumSet.of(RemoteDownloadStatus.FETCHING_METADATA, RemoteDownloadStatus.AWAITING_FILE_SELECTION), "remote download cannot await file selection from current status");
        task.getCandidateFiles().clear();
        task.setSelectedFileCount(0);
        if (candidateFiles != null) {
            for (RemoteDownloadCandidateFile candidateFile : candidateFiles) {
                candidateFile.setSelected(false);
                task.addCandidateFile(candidateFile);
            }
        }
        task.setStatus(RemoteDownloadStatus.AWAITING_FILE_SELECTION);
        remoteDownloadTaskRepository.save(task);
    }

    @Transactional
    public void markImporting(Long remoteDownloadId) {
        RemoteDownloadTask task = remoteDownloadTaskRepository.findById(remoteDownloadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "remote download task not found"));
        requireStatus(task, EnumSet.of(RemoteDownloadStatus.DOWNLOADING), "remote download cannot enter importing from current status");
        task.setStatus(RemoteDownloadStatus.IMPORTING);
        remoteDownloadTaskRepository.save(task);
    }

    @Transactional
    public void markCompleted(Long remoteDownloadId, int importedFileCount) {
        RemoteDownloadTask task = remoteDownloadTaskRepository.findById(remoteDownloadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "remote download task not found"));
        requireStatus(task, EnumSet.of(RemoteDownloadStatus.IMPORTING), "remote download cannot complete from current status");
        task.setStatus(RemoteDownloadStatus.COMPLETED);
        task.setImportedFileCount(Math.max(0, importedFileCount));
        task.setFailureCode(null);
        task.setFailureMessage(null);
        task.setFinishedAt(Instant.now());
        remoteDownloadTaskRepository.save(task);
    }

    @Transactional
    public void markFailed(Long remoteDownloadId, String failureCode, String failureMessage) {
        RemoteDownloadTask task = remoteDownloadTaskRepository.findById(remoteDownloadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "remote download task not found"));
        requireStatus(task, nonTerminalStatuses(), "remote download is already finished");
        task.setStatus(RemoteDownloadStatus.FAILED);
        task.setFailureCode(failureCode);
        task.setFailureMessage(failureMessage);
        task.setFinishedAt(Instant.now());
        remoteDownloadTaskRepository.save(task);
    }

    private RemoteDownloadTask buildTask(Long userId, CreateRemoteDownloadCommand command) {
        if (command.sourceType() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "remote download source type is required");
        }
        return switch (command.sourceType()) {
            case HTTP -> RemoteDownloadTask.createHttp(
                    userId,
                    command.targetPath(),
                    requireTextSource(command.sourceValue(), "http source is required"),
                    "local-default"
            );
            case MAGNET -> RemoteDownloadTask.createMagnet(
                    userId,
                    command.targetPath(),
                    requireTextSource(command.sourceValue(), "magnet source is required"),
                    "local-default"
            );
            case TORRENT_FILE -> RemoteDownloadTask.createTorrent(
                    userId,
                    command.targetPath(),
                    requireTorrentFilename(command.torrentFilename(), command.torrentContent()),
                    command.torrentContent(),
                    "local-default"
            );
        };
    }

    private String requireTextSource(String sourceValue, String message) {
        if (sourceValue == null || sourceValue.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return sourceValue.trim();
    }

    private String requireTorrentFilename(String torrentFilename, byte[] torrentContent) {
        if (torrentContent == null || torrentContent.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "torrent file content is required");
        }
        return torrentFilename == null || torrentFilename.isBlank() ? "upload.torrent" : torrentFilename;
    }

    private Map<String, Object> initialPublicState(RemoteDownloadTask task) {
        Map<String, Object> publicState = new LinkedHashMap<>();
        publicState.put("phase", "pending");
        publicState.put("message", "remote download queued");
        publicState.put("progressPercent", 0);
        publicState.put("processedItems", 0);
        publicState.put("totalItems", 1);
        publicState.put("engineType", task.getEngineType().name());
        publicState.put("sourceType", task.getSourceType().name());
        return publicState;
    }

    private Map<String, Object> initialPrivateState(RemoteDownloadTask task) {
        Map<String, Object> privateState = new LinkedHashMap<>();
        if (task.getId() != null) {
            privateState.put("remoteDownloadId", task.getId());
        }
        return privateState;
    }

    private String correlationId(RemoteDownloadTask task) {
        return "remote-download:" + task.getUserId() + ":" + task.getCreatedAt().toEpochMilli();
    }

    private RemoteDownloadTask requireOwnedTask(Long userId, Long id) {
        return remoteDownloadTaskRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "remote download task not found"));
    }

    private RemoteDownloadListItemResponse toListItemResponse(RemoteDownloadTask task) {
        return new RemoteDownloadListItemResponse(
                task.getId(),
                task.getBackgroundTaskId(),
                task.getStatus().name(),
                task.getSourceType().name(),
                task.getEngineType().name(),
                task.getTargetPath(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFinishedAt()
        );
    }

    private RemoteDownloadDetailResponse toDetailResponse(RemoteDownloadTask task) {
        return new RemoteDownloadDetailResponse(
                task.getId(),
                task.getBackgroundTaskId(),
                task.getStatus().name(),
                task.getSourceType().name(),
                task.getEngineType().name(),
                task.getTargetPath(),
                task.getSourceValue(),
                task.getDownloadNodeId(),
                task.getSelectedFileCount(),
                task.getImportedFileCount(),
                task.getFailureCode(),
                task.getFailureMessage(),
                task.getCandidateFiles().stream()
                        .map(this::toCandidateResponse)
                        .toList(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFinishedAt()
        );
    }

    private RemoteDownloadCandidateFileResponse toCandidateResponse(RemoteDownloadCandidateFile candidateFile) {
        return new RemoteDownloadCandidateFileResponse(
                candidateFile.getFileKey(),
                candidateFile.getRelativePath(),
                candidateFile.getSize(),
                candidateFile.isSelected()
        );
    }

    private void requireStatus(RemoteDownloadTask task, EnumSet<RemoteDownloadStatus> allowedStatuses, String message) {
        if (!allowedStatuses.contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
    }

    private EnumSet<RemoteDownloadStatus> nonTerminalStatuses() {
        return EnumSet.of(
                RemoteDownloadStatus.PENDING,
                RemoteDownloadStatus.SUBMITTED,
                RemoteDownloadStatus.FETCHING_METADATA,
                RemoteDownloadStatus.AWAITING_FILE_SELECTION,
                RemoteDownloadStatus.DOWNLOADING,
                RemoteDownloadStatus.IMPORTING
        );
    }
}
