package com.yoyuzh.files.workspace.api;

public interface WorkspaceDeferredUploadApi {

    FileMetadataResponse enqueueCreate(WebDavWorkspacePutCommand command,
                                       String normalizedParentPath,
                                       String filename);

    FileMetadataResponse enqueueReplace(WebDavWorkspacePutCommand command,
                                        Long targetFileId,
                                        long previousSize);

    record TaskStateSnapshot(
            Long id,
            com.yoyuzh.platform.job.api.BackgroundTaskStatus status
    ) {
    }

    default java.util.Optional<TaskStateSnapshot> findTaskState(Long taskId) {
        return java.util.Optional.empty();
    }
}
