package com.yoyuzh.platform.job.internal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveApi;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveExtractionResult;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractBackgroundTaskHandlerTest {

    @Mock
    private IdentityUserDirectoryApi identityUserDirectoryApi;
    @Mock
    private WorkspaceArchiveApi workspaceArchiveApi;

    private ExtractBackgroundTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExtractBackgroundTaskHandler(
                identityUserDirectoryApi,
                workspaceArchiveApi,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
    }

    @Test
    void shouldDelegateArchiveExtractionAndExposeProgressSummary() {
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(Optional.of(createUser(7L)));
        when(workspaceArchiveApi.extractZipCompatibleArchive(
                eq(new WorkspaceUserContext(7L, 1024L, 1024L)),
                eq(11L),
                eq("/docs"),
                eq("archive"),
                any()
        )).thenReturn(new WorkspaceArchiveExtractionResult("/docs/archive", 2, 2));

        BackgroundTaskHandlerResult result = handler.handle(createExtractTask(11L, 7L, "archive"));

        verify(workspaceArchiveApi).extractZipCompatibleArchive(
                eq(new WorkspaceUserContext(7L, 1024L, 1024L)),
                eq(11L),
                eq("/docs"),
                eq("archive"),
                any()
        );
        assertThat(result.publicStatePatch()).containsEntry("worker", "extract");
        assertThat(result.publicStatePatch()).containsEntry("extractedPath", "/docs/archive");
        assertThat(result.publicStatePatch()).containsEntry("extractedFileCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("extractedDirectoryCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("processedFileCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("totalFileCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("processedDirectoryCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("totalDirectoryCount", 2);
    }

    @Test
    void shouldRejectArchiveTaskWithoutTarget() {
        BackgroundTask task = createExtractTask(21L, 7L, "archive");
        task.setPublicStateJson("{\"fileId\":21}");
        task.setPrivateStateJson("{\"fileId\":21,\"taskType\":\"EXTRACT\"}");

        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("extract task missing output target");

        verify(workspaceArchiveApi, never()).extractZipCompatibleArchive(any(), any(), any(), any(), any());
    }

    @Test
    void shouldWrapMalformedArchiveAsDataStateFailure() {
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(Optional.of(createUser(7L)));
        when(workspaceArchiveApi.extractZipCompatibleArchive(any(), eq(11L), eq("/docs"), eq("archive"), any()))
                .thenThrow(new BusinessException(ErrorCode.ARCHIVE_READ_FAILED, "unstable message"));

        assertThatThrownBy(() -> handler.handle(createExtractTask(11L, 7L, "archive")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("extract task only supports zip-compatible archives");
    }

    @Test
    void shouldNotHoldClassLevelTransactionBoundary() {
        assertThat(ExtractBackgroundTaskHandler.class.isAnnotationPresent(Transactional.class)).isFalse();
    }

    private BackgroundTask createExtractTask(Long fileId, Long userId, String outputDirectoryName) {
        BackgroundTask task = new BackgroundTask();
        task.setId(401L);
        task.setType(BackgroundTaskType.EXTRACT);
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setUserId(userId);
        task.setPublicStateJson("""
                {"fileId":%d,"outputPath":"/docs","outputDirectoryName":"%s"}
                """.formatted(fileId, outputDirectoryName));
        task.setPrivateStateJson("""
                {"fileId":%d,"taskType":"EXTRACT","outputPath":"/docs","outputDirectoryName":"%s"}
                """.formatted(fileId, outputDirectoryName));
        return task;
    }

    private IdentityUserSnapshot createUser(Long id) {
        return new IdentityUserSnapshot(
                id,
                "alice",
                "Alice",
                "alice@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                IdentityRoleName.USER,
                null,
                1024L,
                1024L
        );
    }
}
