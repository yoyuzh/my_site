package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceDeferredBlobFinalizeApi;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BlobUploadTaskHandler implements BackgroundTaskHandler {

    private final FileContentStorage fileContentStorage;
    private final ContentBlobRegistrationApi contentBlobRegistrationApi;
    private final WorkspaceDeferredBlobFinalizeApi workspaceDeferredBlobFinalizeApi;
    private final BackgroundTaskStateManager stateManager;

    public BlobUploadTaskHandler(FileContentStorage fileContentStorage,
                                 ContentBlobRegistrationApi contentBlobRegistrationApi,
                                 WorkspaceDeferredBlobFinalizeApi workspaceDeferredBlobFinalizeApi,
                                 BackgroundTaskStateManager stateManager) {
        this.fileContentStorage = fileContentStorage;
        this.contentBlobRegistrationApi = contentBlobRegistrationApi;
        this.workspaceDeferredBlobFinalizeApi = workspaceDeferredBlobFinalizeApi;
        this.stateManager = stateManager;
    }

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.BLOB_UPLOAD;
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
                "blob upload task state is invalid"
        );
        String mode = requiredText(state.get(BlobUploadTaskState.MODE), "blob upload task missing mode");
        Long blobId = requiredLong(state.get(BlobUploadTaskState.BLOB_ID), "blob upload task missing blobId");
        String objectKey = requiredText(state.get(BlobUploadTaskState.OBJECT_KEY), "blob upload task missing objectKey");
        String localTempPath = requiredText(state.get(BlobUploadTaskState.LOCAL_TEMP_PATH), "blob upload task missing localTempPath");
        String contentType = stateManager.readText(state.get(BlobUploadTaskState.CONTENT_TYPE));
        long size = requiredLong(state.get(BlobUploadTaskState.SIZE), "blob upload task missing size");
        progressReporter.report(Map.of("message", "上传对象存储中"));
        try (InputStream content = Files.newInputStream(Path.of(localTempPath))) {
            fileContentStorage.storeBlob(objectKey, contentType, content, size);
        } catch (IOException ex) {
            throw new IllegalStateException("blob upload task failed to read temp file", ex);
        }
        if (BlobUploadTaskState.REPLACE.equals(mode)) {
            Long targetFileId = requiredLong(state.get(BlobUploadTaskState.TARGET_FILE_ID), "blob upload task missing targetFileId");
            WorkspaceDeferredBlobFinalizeApi.FinalizedReplacement finalizedReplacement = workspaceDeferredBlobFinalizeApi.finalizeDeferredReplace(
                    task.getUserId(),
                    targetFileId,
                    blobId,
                    contentType,
                    size
            );
            workspaceDeferredBlobFinalizeApi.finalizeReplace(
                    task.getUserId(),
                    targetFileId,
                    finalizedReplacement.contentType(),
                    finalizedReplacement.size(),
                    finalizedReplacement.blobId(),
                    finalizedReplacement.objectKey(),
                    finalizedReplacement.primaryEntityId()
            );
        }
        contentBlobRegistrationApi.markBlobReady(blobId);
        workspaceDeferredBlobFinalizeApi.deletePendingTempFile(localTempPath);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("message", "上传完成");
        patch.put("blobId", blobId);
        return new BackgroundTaskHandlerResult(patch);
    }

    private Long requiredLong(Object value, String message) {
        Long result = stateManager.readLong(value);
        if (result == null) {
            throw new IllegalStateException(message);
        }
        return result;
    }

    private String requiredText(Object value, String message) {
        String result = stateManager.readText(value);
        if (result == null || result.isBlank()) {
            throw new IllegalStateException(message);
        }
        return result;
    }
}
