package com.yoyuzh.files.workspace.api;

import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.RegisteredContentFile;

import java.io.IOException;
import java.io.InputStream;

public interface WorkspaceDeferredUploadStagingApi {

    DeferredCreateStage prepareDeferredCreate(WorkspaceUserContext user,
                                             String path,
                                             String filename,
                                             String contentType,
                                             long size,
                                             InputStream contentStream) throws IOException;

    DeferredReplaceStage prepareDeferredReplace(WorkspaceUserContext user,
                                               Long fileId,
                                               String contentType,
                                               long size,
                                               long previousSize,
                                               InputStream contentStream) throws IOException;

    void attachDeferredBlobTask(Long blobId, Long uploadTaskId);

    void cleanupFailedDeferredBlob(Long blobId, String localTempPath);

    FileMetadataResponse readFileMetadata(Long fileId, Long userId);

    record DeferredCreateStage(String normalizedPath,
                               RegisteredContentFile file,
                               ContentBlobReference blob,
                               String localTempPath,
                               String contentType) {
    }

    record DeferredReplaceStage(Long fileId,
                                ContentBlobReference blob,
                                String localTempPath,
                                String contentType,
                                long size,
                                Long oldBlobId,
                                Long oldPrimaryEntityId) {
    }
}
