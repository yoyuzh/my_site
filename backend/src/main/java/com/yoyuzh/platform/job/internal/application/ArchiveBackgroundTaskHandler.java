package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveApi;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveSummary;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Transactional
public class ArchiveBackgroundTaskHandler implements BackgroundTaskHandler {

    private final IdentityUserDirectoryApi identityUserDirectoryApi;
    private final WorkspaceArchiveApi workspaceArchiveApi;
    private final WorkspaceBootstrapApi workspaceBootstrapApi;
    private final BackgroundTaskStateManager stateManager;

    public ArchiveBackgroundTaskHandler(IdentityUserDirectoryApi identityUserDirectoryApi,
                                        WorkspaceArchiveApi workspaceArchiveApi,
                                        WorkspaceBootstrapApi workspaceBootstrapApi,
                                        BackgroundTaskStateManager stateManager) {
        this.identityUserDirectoryApi = identityUserDirectoryApi;
        this.workspaceArchiveApi = workspaceArchiveApi;
        this.workspaceBootstrapApi = workspaceBootstrapApi;
        this.stateManager = stateManager;
    }

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.ARCHIVE;
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task) {
        return handle(task, publicStatePatch -> {
        });
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        Map<String, Object> state = stateManager.mergeJsonObjects(
                task.getPublicStateJson(),
                task.getPrivateStateJson(),
                "archive task state is invalid"
        );
        Long fileId = stateManager.readLong(state.get("fileId"));
        String outputPath = stateManager.readText(state.get("outputPath"));
        String outputFilename = stateManager.readText(state.get("outputFilename"));
        if (fileId == null) {
            throw new IllegalStateException("archive task missing fileId");
        }
        if (!StringUtils.hasText(outputPath) || !StringUtils.hasText(outputFilename)) {
            throw new IllegalStateException("archive task missing output target");
        }

        IdentityUserSnapshot user = identityUserDirectoryApi.findSnapshotById(task.getUserId())
                .orElseThrow(() -> new IllegalStateException("archive task user not found"));

        WorkspaceArchiveSummary summary = workspaceArchiveApi.summarizeArchiveSource(task.getUserId(), fileId);
        progressReporter.report(progressPatch(0, summary.fileCount(), 0, summary.directoryCount()));
        byte[] archiveBytes = workspaceArchiveApi.buildArchiveBytes(task.getUserId(), fileId, progress ->
                progressReporter.report(progressPatch(
                        progress.processedFileCount(),
                        progress.totalFileCount(),
                        progress.processedDirectoryCount(),
                        progress.totalDirectoryCount()
                )));
        FileMetadataResponse archivedFile = workspaceBootstrapApi.importExternalFile(
                workspaceUser(user),
                outputPath,
                outputFilename,
                "application/zip",
                archiveBytes.length,
                archiveBytes
        );

        Map<String, Object> publicStatePatch = new LinkedHashMap<>();
        publicStatePatch.put("worker", "archive");
        publicStatePatch.put("archivedFileId", archivedFile.id());
        publicStatePatch.put("archivedFilename", archivedFile.filename());
        publicStatePatch.put("archivedPath", archivedFile.path());
        publicStatePatch.put("archiveSize", archiveBytes.length);
        publicStatePatch.putAll(progressPatch(
                summary.fileCount(),
                summary.fileCount(),
                summary.directoryCount(),
                summary.directoryCount()
        ));
        return new BackgroundTaskHandlerResult(publicStatePatch);
    }

    private Map<String, Object> progressPatch(int processedFileCount,
                                              int totalFileCount,
                                              int processedDirectoryCount,
                                              int totalDirectoryCount) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("processedFileCount", processedFileCount);
        patch.put("totalFileCount", totalFileCount);
        patch.put("processedDirectoryCount", processedDirectoryCount);
        patch.put("totalDirectoryCount", totalDirectoryCount);
        patch.put("progressPercent", calculateProgressPercent(
                processedFileCount,
                totalFileCount,
                processedDirectoryCount,
                totalDirectoryCount
        ));
        return patch;
    }

    private int calculateProgressPercent(int processedFileCount,
                                         int totalFileCount,
                                         int processedDirectoryCount,
                                         int totalDirectoryCount) {
        int total = Math.max(0, totalFileCount) + Math.max(0, totalDirectoryCount);
        int processed = Math.max(0, processedFileCount) + Math.max(0, processedDirectoryCount);
        if (total <= 0) {
            return 100;
        }
        return Math.min(100, (int) Math.floor((processed * 100.0d) / total));
    }

    private WorkspaceUserContext workspaceUser(IdentityUserSnapshot user) {
        return new WorkspaceUserContext(
                user.id(),
                user.storageQuotaBytes(),
                user.maxUploadSizeBytes()
        );
    }

}
