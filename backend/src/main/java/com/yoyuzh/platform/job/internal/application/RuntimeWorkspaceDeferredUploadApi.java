package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WebDavWorkspacePutCommand;
import com.yoyuzh.files.workspace.api.WorkspaceDeferredUploadApi;
import com.yoyuzh.files.workspace.api.WorkspaceDeferredUploadStagingApi;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RuntimeWorkspaceDeferredUploadApi implements WorkspaceDeferredUploadApi {

    private final WorkspaceDeferredUploadStagingApi workspaceDeferredUploadStagingApi;
    private final BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    private final BackgroundTaskWorker backgroundTaskWorker;
    private final BackgroundTaskService backgroundTaskService;

    public RuntimeWorkspaceDeferredUploadApi(WorkspaceDeferredUploadStagingApi workspaceDeferredUploadStagingApi,
                                             BackgroundTaskLifecycleApi backgroundTaskLifecycleApi,
                                             BackgroundTaskWorker backgroundTaskWorker,
                                             BackgroundTaskService backgroundTaskService) {
        this.workspaceDeferredUploadStagingApi = workspaceDeferredUploadStagingApi;
        this.backgroundTaskLifecycleApi = backgroundTaskLifecycleApi;
        this.backgroundTaskWorker = backgroundTaskWorker;
        this.backgroundTaskService = backgroundTaskService;
    }

    @Override
    public FileMetadataResponse enqueueCreate(WebDavWorkspacePutCommand command,
                                              String normalizedParentPath,
                                              String filename) {
        WorkspaceDeferredUploadStagingApi.DeferredCreateStage deferredCreate = null;
        try {
            deferredCreate = workspaceDeferredUploadStagingApi.prepareDeferredCreate(
                    command.user(),
                    normalizedParentPath,
                    filename,
                    command.contentType(),
                    command.size(),
                    command.content()
            );
            Long taskId = enqueueTask(
                    command.user().userId(),
                    buildCreatePublicState(deferredCreate),
                    buildCreatePrivateState(deferredCreate)
            );
            workspaceDeferredUploadStagingApi.attachDeferredBlobTask(deferredCreate.blob().blobId(), taskId);
            backgroundTaskWorker.wakeLightweightTasks();
            return toResponse(deferredCreate.file());
        } catch (RuntimeException | Error ex) {
            if (deferredCreate != null) {
                workspaceDeferredUploadStagingApi.cleanupFailedDeferredBlob(
                        deferredCreate.blob().blobId(),
                        deferredCreate.localTempPath()
                );
            }
            throw ex;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to stage WebDAV file content", ex);
        }
    }

    @Override
    public FileMetadataResponse enqueueReplace(WebDavWorkspacePutCommand command,
                                               Long targetFileId,
                                               long previousSize) {
        WorkspaceDeferredUploadStagingApi.DeferredReplaceStage deferredReplace = null;
        try {
            deferredReplace = workspaceDeferredUploadStagingApi.prepareDeferredReplace(
                    command.user(),
                    targetFileId,
                    command.contentType(),
                    command.size(),
                    previousSize,
                    command.content()
            );
            Long taskId = enqueueTask(
                    command.user().userId(),
                    buildReplacePublicState(deferredReplace),
                    buildReplacePrivateState(deferredReplace)
            );
            workspaceDeferredUploadStagingApi.attachDeferredBlobTask(deferredReplace.blob().blobId(), taskId);
            backgroundTaskWorker.wakeLightweightTasks();
            return workspaceDeferredUploadStagingApi.readFileMetadata(targetFileId, command.user().userId());
        } catch (RuntimeException | Error ex) {
            if (deferredReplace != null) {
                workspaceDeferredUploadStagingApi.cleanupFailedDeferredBlob(
                        deferredReplace.blob().blobId(),
                        deferredReplace.localTempPath()
                );
            }
            throw ex;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to stage WebDAV replacement content", ex);
        }
    }

    @Override
    public java.util.Optional<TaskStateSnapshot> findTaskState(Long taskId) {
        if (taskId == null) {
            return java.util.Optional.empty();
        }
        return backgroundTaskService.findTaskById(taskId)
                .map(task -> new TaskStateSnapshot(task.getId(), task.getStatus()));
    }

    private Long enqueueTask(Long userId, Map<String, Object> publicState, Map<String, Object> privateState) {
        return backgroundTaskLifecycleApi.createQueuedTaskByUserId(
                userId,
                BackgroundTaskType.BLOB_UPLOAD,
                publicState,
                privateState,
                "blob-upload:" + UUID.randomUUID().toString().replace("-", "")
        ).id();
    }

    private Map<String, Object> buildCreatePublicState(WorkspaceDeferredUploadStagingApi.DeferredCreateStage deferredCreate) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("message", "文件上传处理中");
        state.put("fileId", deferredCreate.file().id());
        state.put("path", deferredCreate.normalizedPath());
        state.put("filename", deferredCreate.file().filename());
        return state;
    }

    private Map<String, Object> buildCreatePrivateState(WorkspaceDeferredUploadStagingApi.DeferredCreateStage deferredCreate) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(BlobUploadTaskState.MODE, BlobUploadTaskState.CREATE);
        state.put(BlobUploadTaskState.BLOB_ID, deferredCreate.blob().blobId());
        state.put(BlobUploadTaskState.OBJECT_KEY, deferredCreate.blob().objectKey());
        state.put(BlobUploadTaskState.LOCAL_TEMP_PATH, deferredCreate.localTempPath());
        state.put(BlobUploadTaskState.CONTENT_TYPE, deferredCreate.contentType());
        state.put(BlobUploadTaskState.SIZE, deferredCreate.blob().size());
        state.put(BlobUploadTaskState.FILE_ID, deferredCreate.file().id());
        state.put(BlobUploadTaskState.PATH, deferredCreate.normalizedPath());
        state.put(BlobUploadTaskState.FILENAME, deferredCreate.file().filename());
        return state;
    }

    private Map<String, Object> buildReplacePublicState(WorkspaceDeferredUploadStagingApi.DeferredReplaceStage deferredReplace) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("message", "文件替换处理中");
        state.put("fileId", deferredReplace.fileId());
        return state;
    }

    private Map<String, Object> buildReplacePrivateState(WorkspaceDeferredUploadStagingApi.DeferredReplaceStage deferredReplace) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(BlobUploadTaskState.MODE, BlobUploadTaskState.REPLACE);
        state.put(BlobUploadTaskState.BLOB_ID, deferredReplace.blob().blobId());
        state.put(BlobUploadTaskState.OBJECT_KEY, deferredReplace.blob().objectKey());
        state.put(BlobUploadTaskState.LOCAL_TEMP_PATH, deferredReplace.localTempPath());
        state.put(BlobUploadTaskState.CONTENT_TYPE, deferredReplace.contentType());
        state.put(BlobUploadTaskState.SIZE, deferredReplace.size());
        state.put(BlobUploadTaskState.TARGET_FILE_ID, deferredReplace.fileId());
        state.put(BlobUploadTaskState.OLD_BLOB_ID, deferredReplace.oldBlobId());
        state.put(BlobUploadTaskState.OLD_PRIMARY_ENTITY_ID, deferredReplace.oldPrimaryEntityId());
        return state;
    }

    private FileMetadataResponse toResponse(com.yoyuzh.files.content.api.RegisteredContentFile file) {
        return new FileMetadataResponse(
                file.id(),
                file.filename(),
                file.path(),
                file.size(),
                file.contentType(),
                file.directory(),
                file.createdAt(),
                file.createdAt(),
                null,
                null,
                false
        );
    }
}
