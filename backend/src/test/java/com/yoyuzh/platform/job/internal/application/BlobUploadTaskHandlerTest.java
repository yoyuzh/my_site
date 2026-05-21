package com.yoyuzh.platform.job.internal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceDeferredBlobFinalizeApi;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlobUploadTaskHandlerTest {

    @Test
    void shouldUploadCreateBlobAndMarkReady() throws Exception {
        FileContentStorage storage = mock(FileContentStorage.class);
        ContentBlobRegistrationApi blobRegistrationApi = mock(ContentBlobRegistrationApi.class);
        WorkspaceDeferredBlobFinalizeApi finalizeApi = mock(WorkspaceDeferredBlobFinalizeApi.class);
        BlobUploadTaskHandler handler = new BlobUploadTaskHandler(
                storage,
                blobRegistrationApi,
                finalizeApi,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
        Path tempFile = Files.createTempFile("blob-upload-handler", ".tmp");
        Files.writeString(tempFile, "hello");

        BackgroundTask task = task(Map.of(
                BlobUploadTaskState.MODE, BlobUploadTaskState.CREATE,
                BlobUploadTaskState.BLOB_ID, 11L,
                BlobUploadTaskState.OBJECT_KEY, "blobs/11",
                BlobUploadTaskState.LOCAL_TEMP_PATH, tempFile.toString(),
                BlobUploadTaskState.CONTENT_TYPE, "text/plain",
                BlobUploadTaskState.SIZE, 5L
        ));

        BackgroundTaskHandlerResult result = handler.handle(task);

        assertThat(result.completed()).isTrue();
        verify(storage).storeBlob(eq("blobs/11"), eq("text/plain"), any(InputStream.class), eq(5L));
        verify(blobRegistrationApi).markBlobReady(11L);
        verify(finalizeApi).deletePendingTempFile(tempFile.toString());
        verify(finalizeApi, never()).finalizeReplace(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFinalizeReplaceAfterUpload() throws Exception {
        FileContentStorage storage = mock(FileContentStorage.class);
        ContentBlobRegistrationApi blobRegistrationApi = mock(ContentBlobRegistrationApi.class);
        WorkspaceDeferredBlobFinalizeApi finalizeApi = mock(WorkspaceDeferredBlobFinalizeApi.class);
        BlobUploadTaskHandler handler = new BlobUploadTaskHandler(
                storage,
                blobRegistrationApi,
                finalizeApi,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
        Path tempFile = Files.createTempFile("blob-upload-replace", ".tmp");
        Files.writeString(tempFile, "world");
        when(finalizeApi.finalizeDeferredReplace(7L, 22L, 12L, "text/plain", 5L))
                .thenReturn(new WorkspaceDeferredBlobFinalizeApi.FinalizedReplacement(12L, "blobs/12", 33L, "text/plain", 5L));

        BackgroundTask task = task(Map.of(
                BlobUploadTaskState.MODE, BlobUploadTaskState.REPLACE,
                BlobUploadTaskState.BLOB_ID, 12L,
                BlobUploadTaskState.OBJECT_KEY, "blobs/12",
                BlobUploadTaskState.LOCAL_TEMP_PATH, tempFile.toString(),
                BlobUploadTaskState.CONTENT_TYPE, "text/plain",
                BlobUploadTaskState.SIZE, 5L,
                BlobUploadTaskState.TARGET_FILE_ID, 22L
        ));

        handler.handle(task);

        verify(finalizeApi).finalizeDeferredReplace(7L, 22L, 12L, "text/plain", 5L);
        verify(finalizeApi).finalizeReplace(7L, 22L, "text/plain", 5L, 12L, "blobs/12", 33L);
        verify(blobRegistrationApi).markBlobReady(12L);
    }

    private BackgroundTask task(Map<String, Object> privateState) {
        BackgroundTask task = new BackgroundTask();
        task.setId(1L);
        task.setUserId(7L);
        task.setType(BackgroundTaskType.BLOB_UPLOAD);
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setPrivateStateJson(new BackgroundTaskStateManager(new ObjectMapper()).toJson(privateState));
        task.setPublicStateJson("{}");
        return task;
    }
}
