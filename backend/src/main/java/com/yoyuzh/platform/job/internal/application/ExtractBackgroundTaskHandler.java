package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceArchiveApi;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveExtractionResult;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ExtractBackgroundTaskHandler implements BackgroundTaskHandler {

    private final IdentityUserDirectoryApi identityUserDirectoryApi;
    private final WorkspaceArchiveApi workspaceArchiveApi;
    private final BackgroundTaskStateManager stateManager;

    public ExtractBackgroundTaskHandler(IdentityUserDirectoryApi identityUserDirectoryApi,
                                        WorkspaceArchiveApi workspaceArchiveApi,
                                        BackgroundTaskStateManager stateManager) {
        this.identityUserDirectoryApi = identityUserDirectoryApi;
        this.workspaceArchiveApi = workspaceArchiveApi;
        this.stateManager = stateManager;
    }

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.EXTRACT;
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
                "extract task state is invalid"
        );
        Long fileId = stateManager.readLong(state.get("fileId"));
        String outputPath = stateManager.readText(state.get("outputPath"));
        String outputDirectoryName = stateManager.readText(state.get("outputDirectoryName"));
        if (fileId == null) {
            throw new IllegalStateException("extract task missing fileId");
        }
        if (!StringUtils.hasText(outputPath) || !StringUtils.hasText(outputDirectoryName)) {
            throw new IllegalStateException("extract task missing output target");
        }

        IdentityUserSnapshot user = identityUserDirectoryApi.findSnapshotById(task.getUserId())
                .orElseThrow(() -> new IllegalStateException("extract task user not found"));

        progressReporter.report(progressPatch(0, 0, 0, 0));
        WorkspaceArchiveExtractionResult extractionResult;
        try {
            extractionResult = workspaceArchiveApi.extractArchive(
                    workspaceUser(user),
                    fileId,
                    outputPath,
                    outputDirectoryName,
                    progress -> progressReporter.report(progressPatch(
                            progress.processedFileCount(),
                            progress.totalFileCount(),
                            progress.processedDirectoryCount(),
                            progress.totalDirectoryCount()
                    ))
            );
        } catch (BusinessException ex) {
            if (isUnsupportedArchive(ex)) {
                throw new IllegalStateException("extract task only supports supported archive files", ex);
            }
            throw ex;
        }

        Map<String, Object> publicStatePatch = new LinkedHashMap<>();
        publicStatePatch.put("worker", "extract");
        publicStatePatch.put("extractedPath", extractionResult.extractedPath());
        publicStatePatch.put("extractedFileCount", extractionResult.extractedFileCount());
        publicStatePatch.put("extractedDirectoryCount", extractionResult.extractedDirectoryCount());
        publicStatePatch.putAll(progressPatch(
                extractionResult.extractedFileCount(),
                extractionResult.extractedFileCount(),
                extractionResult.extractedDirectoryCount(),
                extractionResult.extractedDirectoryCount()
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

    private boolean isUnsupportedArchive(BusinessException ex) {
        return ex.getErrorCode() == ErrorCode.ARCHIVE_READ_FAILED;
    }
}
