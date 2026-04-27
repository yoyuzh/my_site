package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.transfer.api.RemoteDownloadExecutionApi;
import com.yoyuzh.transfer.api.RemoteDownloadExecutionResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RemoteDownloadBackgroundTaskHandler implements BackgroundTaskHandler {

    private final RemoteDownloadExecutionApi remoteDownloadExecutionApi;
    private final BackgroundTaskStateManager stateManager;

    public RemoteDownloadBackgroundTaskHandler(RemoteDownloadExecutionApi remoteDownloadExecutionApi,
                                               BackgroundTaskStateManager stateManager) {
        this.remoteDownloadExecutionApi = remoteDownloadExecutionApi;
        this.stateManager = stateManager;
    }

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.REMOTE_DOWNLOAD;
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        Long remoteDownloadId = readRemoteDownloadId(task);
        RemoteDownloadExecutionResult result = remoteDownloadExecutionApi.start(remoteDownloadId);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("worker", "remote-download");
        patch.put("phase", result.phase());
        patch.put("engineType", result.engineType());
        patch.put("downloaderTaskId", result.downloaderTaskId());
        progressReporter.report(patch);
        if (result.completed()) {
            return new BackgroundTaskHandlerResult(patch);
        }
        return BackgroundTaskHandlerResult.reschedule(patch, result.nextRunDelaySeconds() == null ? 1L : result.nextRunDelaySeconds());
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task) {
        return handle(task, ignored -> {
        });
    }

    private Long readRemoteDownloadId(BackgroundTask task) {
        Map<String, Object> privateState = stateManager.parseJsonObject(
                task.getPrivateStateJson(),
                "remote download task state is invalid"
        );
        Long remoteDownloadId = stateManager.readLong(privateState.get("remoteDownloadId"));
        if (remoteDownloadId == null) {
            throw new IllegalStateException("remote download task missing remoteDownloadId");
        }
        return remoteDownloadId;
    }
}
