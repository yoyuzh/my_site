package com.yoyuzh.platform.job.internal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.workspace.api.FileDeleteMode;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceBackgroundMutationApi;
import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import com.yoyuzh.files.workspace.api.WorkspaceMoveItemResult;
import com.yoyuzh.files.workspace.api.WorkspaceMoveOutcomeStatus;
import com.yoyuzh.files.workspace.api.WorkspaceMoveResult;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceMutationBackgroundTaskHandlerTest {

    @Mock
    private WorkspaceBackgroundMutationApi workspaceBackgroundMutationApi;

    private WorkspaceMutationBackgroundTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WorkspaceMutationBackgroundTaskHandler(
                workspaceBackgroundMutationApi,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
    }

    @Test
    void shouldDeleteItemsOneByOneAndReportProgress() {
        BackgroundTask task = task("""
                {
                  "operation": "DELETE",
                  "fileIds": [11, 12],
                  "deleteMode": "RECYCLE"
                }
                """);
        List<Map<String, Object>> progressPatches = new ArrayList<>();

        BackgroundTaskHandlerResult result = handler.handle(task, progressPatches::add);

        verify(workspaceBackgroundMutationApi).delete(7L, 11L, FileDeleteMode.RECYCLE);
        verify(workspaceBackgroundMutationApi).delete(7L, 12L, FileDeleteMode.RECYCLE);
        assertThat(progressPatches).extracting(patch -> patch.get("processedItems")).containsExactly(1, 2);
        assertThat(result.publicStatePatch()).containsEntry("operation", "DELETE");
        assertThat(result.publicStatePatch()).containsEntry("processedItems", 2);
        assertThat(result.publicStatePatch()).containsEntry("totalItems", 2);
        assertThat(result.publicStatePatch()).containsEntry("progressPercent", 100);
    }

    @Test
    void shouldMoveBatchThroughWorkspaceApi() {
        BackgroundTask task = task("""
                {
                  "operation": "MOVE",
                  "fileIds": [21, 22],
                  "targetPath": "/docs",
                  "conflictStrategy": "AUTO_RENAME"
                }
                """);
        WorkspaceMoveResult moveResult = WorkspaceMoveResult.success(List.of(
                new WorkspaceMoveItemResult(21L, "a.txt", "/old/a.txt", "/docs/a.txt", false, false, null, null),
                new WorkspaceMoveItemResult(22L, "b.txt", "/old/b.txt", "/docs/b.txt", false, false, null, null)
        ));
        when(workspaceBackgroundMutationApi.batchMove(
                7L,
                List.of(21L, 22L),
                "/docs",
                WorkspaceMoveConflictStrategy.AUTO_RENAME
        )).thenReturn(moveResult);

        BackgroundTaskHandlerResult result = handler.handle(task, progress -> {
        });

        assertThat(result.publicStatePatch()).containsEntry("operation", "MOVE");
        assertThat(result.publicStatePatch()).containsEntry("moveStatus", WorkspaceMoveOutcomeStatus.SUCCESS.name());
        assertThat(result.publicStatePatch()).containsEntry("processedItems", 2);
        assertThat(result.publicStatePatch()).containsEntry("totalItems", 2);
    }

    @Test
    void shouldRenameThroughWorkspaceApi() {
        BackgroundTask task = task("""
                {
                  "operation": "RENAME",
                  "fileId": 31,
                  "filename": "renamed.txt"
                }
                """);
        when(workspaceBackgroundMutationApi.rename(7L, 31L, "renamed.txt"))
                .thenReturn(new FileMetadataResponse(
                        31L,
                        "renamed.txt",
                        "/docs",
                        12L,
                        "text/plain",
                        false,
                        null,
                        null,
                        null,
                        null,
                        false
                ));

        BackgroundTaskHandlerResult result = handler.handle(task, progress -> {
        });

        verify(workspaceBackgroundMutationApi).rename(7L, 31L, "renamed.txt");
        assertThat(result.publicStatePatch()).containsEntry("operation", "RENAME");
        assertThat(result.publicStatePatch()).containsEntry("processedItems", 1);
        assertThat(result.publicStatePatch()).containsEntry("totalItems", 1);
    }

    private BackgroundTask task(String privateStateJson) {
        BackgroundTask task = new BackgroundTask();
        task.setId(99L);
        task.setUserId(7L);
        task.setType(BackgroundTaskType.WORKSPACE_MUTATION);
        task.setPublicStateJson("{}");
        task.setPrivateStateJson(privateStateJson);
        return task;
    }
}
