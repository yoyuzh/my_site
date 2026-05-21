package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.workspace.api.WebDavWorkspacePutCommand;
import com.yoyuzh.files.workspace.api.WorkspaceDeferredUploadStagingApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspaceDeferredUploadApiTest {

    @Mock
    private WorkspaceDeferredUploadStagingApi workspaceDeferredUploadStagingApi;
    @Mock
    private BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    @Mock
    private BackgroundTaskWorker backgroundTaskWorker;
    @Mock
    private BackgroundTaskService backgroundTaskService;

    @Test
    void shouldCleanupDeferredCreateStageWhenTaskEnqueueFails() throws Exception {
        RuntimeWorkspaceDeferredUploadApi api = new RuntimeWorkspaceDeferredUploadApi(
                workspaceDeferredUploadStagingApi,
                backgroundTaskLifecycleApi,
                backgroundTaskWorker,
                backgroundTaskService
        );
        WorkspaceDeferredUploadStagingApi.DeferredCreateStage stage = new WorkspaceDeferredUploadStagingApi.DeferredCreateStage(
                "/Docs",
                new RegisteredContentFile(21L, "a.txt", "/Docs", 5L, "text/plain", false, LocalDateTime.now()),
                new ContentBlobReference(11L, "blobs/11", "text/plain", 5L),
                "/tmp/pending-create",
                "text/plain"
        );
        when(workspaceDeferredUploadStagingApi.prepareDeferredCreate(any(), eq("/Docs"), eq("a.txt"), eq("text/plain"), eq(5L), any()))
                .thenReturn(stage);
        when(backgroundTaskLifecycleApi.createQueuedTaskByUserId(any(), any(), anyMap(), anyMap(), anyString()))
                .thenThrow(new IllegalStateException("queue failed"));

        assertThatThrownBy(() -> api.enqueueCreate(command("/Docs/a.txt", "hello"), "/Docs", "a.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("queue failed");

        verify(workspaceDeferredUploadStagingApi).cleanupFailedDeferredBlob(11L, "/tmp/pending-create");
        verify(backgroundTaskWorker, never()).wakeLightweightTasks();
    }

    @Test
    void shouldCleanupDeferredReplaceStageWhenTaskEnqueueFails() throws Exception {
        RuntimeWorkspaceDeferredUploadApi api = new RuntimeWorkspaceDeferredUploadApi(
                workspaceDeferredUploadStagingApi,
                backgroundTaskLifecycleApi,
                backgroundTaskWorker,
                backgroundTaskService
        );
        WorkspaceDeferredUploadStagingApi.DeferredReplaceStage stage = new WorkspaceDeferredUploadStagingApi.DeferredReplaceStage(
                22L,
                new ContentBlobReference(12L, "blobs/12", "text/plain", 5L),
                "/tmp/pending-replace",
                "text/plain",
                5L,
                90L,
                91L
        );
        when(workspaceDeferredUploadStagingApi.prepareDeferredReplace(any(), eq(22L), eq("text/plain"), eq(5L), eq(3L), any()))
                .thenReturn(stage);
        when(backgroundTaskLifecycleApi.createQueuedTaskByUserId(any(), any(), anyMap(), anyMap(), anyString()))
                .thenThrow(new IllegalStateException("queue failed"));

        assertThatThrownBy(() -> api.enqueueReplace(command("/Docs/a.txt", "world"), 22L, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("queue failed");

        verify(workspaceDeferredUploadStagingApi).cleanupFailedDeferredBlob(12L, "/tmp/pending-replace");
        verify(backgroundTaskWorker, never()).wakeLightweightTasks();
    }

    private WebDavWorkspacePutCommand command(String logicalPath, String body) {
        byte[] bytes = body.getBytes(UTF_8);
        return new WebDavWorkspacePutCommand(
                new WorkspaceUserContext(7L, 1024L, 1024L),
                logicalPath,
                "text/plain",
                bytes.length,
                new ByteArrayInputStream(bytes),
                true
        );
    }
}
