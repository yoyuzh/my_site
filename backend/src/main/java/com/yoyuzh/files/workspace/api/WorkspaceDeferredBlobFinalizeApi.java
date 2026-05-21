package com.yoyuzh.files.workspace.api;

public interface WorkspaceDeferredBlobFinalizeApi {

    FinalizedReplacement finalizeDeferredReplace(Long userId,
                                                 Long fileId,
                                                 Long blobId,
                                                 String contentType,
                                                 long size);

    void deletePendingTempFile(String localTempPath);

    void finalizeReplace(Long userId,
                         Long targetFileId,
                         String contentType,
                         long size,
                         Long newBlobId,
                         String newObjectKey,
                         Long newPrimaryEntityId);

    record FinalizedReplacement(Long blobId,
                                String objectKey,
                                Long primaryEntityId,
                                String contentType,
                                long size) {
    }
}
