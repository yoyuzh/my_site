package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.content.api.ContentBlobReadApi;
import com.yoyuzh.files.content.api.ContentBlobReadResult;
import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.search.api.FileMetadataWriteApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileQueryApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaMetadataBackgroundTaskHandlerTest {

    @Mock
    private WorkspaceFileQueryApi workspaceFileQueryApi;
    @Mock
    private ContentBlobQueryApi contentBlobQueryApi;
    @Mock
    private FileMetadataWriteApi fileMetadataWriteApi;
    @Mock
    private ContentBlobReadApi contentBlobReadApi;

    private MediaMetadataBackgroundTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MediaMetadataBackgroundTaskHandler(
                workspaceFileQueryApi,
                contentBlobQueryApi,
                fileMetadataWriteApi,
                contentBlobReadApi,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
    }

    @Test
    void shouldExtractImageMetadataFromPngBlob() throws Exception {
        BackgroundTask task = createTask(11L);
        WorkspaceFileSnapshot file = createFile(11L, false, "image/png", 64L, 100L);
        byte[] pngBytes = createPngBytes(2, 1);

        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 11L)).thenReturn(Optional.of(file));
        when(contentBlobQueryApi.findBlobReferenceById(100L))
                .thenReturn(Optional.of(new ContentBlobReference(100L, "blobs/photo.png", "image/png", 64L)));
        when(contentBlobReadApi.readBlob(100L, false))
                .thenReturn(new ContentBlobReadResult(
                        new ContentBlobReference(100L, "blobs/photo.png", "image/png", 64L),
                        new ByteArrayInputStream(pngBytes),
                        64L,
                        false
                ));

        BackgroundTaskHandlerResult result = handler.handle(task);

        assertThat(result.publicStatePatch()).containsEntry("worker", "media-metadata");
        assertThat(result.publicStatePatch()).containsEntry("metadataExtracted", true);
        assertThat(result.publicStatePatch()).containsEntry("mediaContentType", "image/png");
        assertThat(result.publicStatePatch()).containsEntry("mediaSize", 64L);
        assertThat(result.publicStatePatch()).containsEntry("mediaWidth", 2);
        assertThat(result.publicStatePatch()).containsEntry("mediaHeight", 1);
        verify(contentBlobReadApi).readBlob(100L, false);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileMetadataWriteApi, times(4)).upsertPublicMetadata(any(), nameCaptor.capture(), valueCaptor.capture());
        assertThat(nameCaptor.getAllValues())
                .containsExactly("media:contentType", "media:size", "media:width", "media:height");
        assertThat(valueCaptor.getAllValues())
                .containsExactly("image/png", "64", "2", "1");
    }

    @Test
    void shouldWriteBaseMetadataForVideoBlobWithoutDimensions() {
        BackgroundTask task = createTask(12L);
        WorkspaceFileSnapshot file = createFile(12L, false, "video/mp4", 128L, 100L);

        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 12L)).thenReturn(Optional.of(file));
        when(contentBlobQueryApi.findBlobReferenceById(100L))
                .thenReturn(Optional.of(new ContentBlobReference(100L, "blobs/movie.mp4", "video/mp4", 128L)));
        when(contentBlobReadApi.readBlob(100L, false))
                .thenReturn(new ContentBlobReadResult(
                        new ContentBlobReference(100L, "blobs/movie.mp4", "video/mp4", 128L),
                        new ByteArrayInputStream(new byte[] {0, 1, 2}),
                        128L,
                        false
                ));

        BackgroundTaskHandlerResult result = handler.handle(task);

        assertThat(result.publicStatePatch()).containsEntry("worker", "media-metadata");
        assertThat(result.publicStatePatch()).containsEntry("metadataExtracted", true);
        assertThat(result.publicStatePatch()).containsEntry("mediaContentType", "video/mp4");
        assertThat(result.publicStatePatch()).containsEntry("mediaSize", 128L);
        assertThat(result.publicStatePatch()).doesNotContainKeys("mediaWidth", "mediaHeight");
        verify(fileMetadataWriteApi, times(2)).upsertPublicMetadata(any(), any(), any());
        verify(contentBlobReadApi).readBlob(100L, false);
    }

    @Test
    void shouldRejectMissingFileDirectoryOrBlob() {
        BackgroundTask task = createTask(13L);

        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 13L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("media metadata task file not found");

        WorkspaceFileSnapshot directory = createFile(13L, true, null, 0L, null);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 13L)).thenReturn(Optional.of(directory));
        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("media metadata task only supports files");

        WorkspaceFileSnapshot missingBlob = createFile(13L, false, "image/png", 10L, null);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 13L)).thenReturn(Optional.of(missingBlob));
        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("media metadata task requires blob");
    }

    @Test
    void shouldKeepNoopHandlerOutOfArchiveExtractAndMediaMetadata() {
        NoopBackgroundTaskHandler noop = new NoopBackgroundTaskHandler();
        assertThat(noop.supports(BackgroundTaskType.ARCHIVE)).isFalse();
        assertThat(noop.supports(BackgroundTaskType.EXTRACT)).isFalse();
        assertThat(noop.supports(BackgroundTaskType.MEDIA_META)).isFalse();
    }

    @Test
    void shouldNotHoldClassLevelTransactionBoundary() {
        assertThat(MediaMetadataBackgroundTaskHandler.class.isAnnotationPresent(Transactional.class)).isFalse();
    }

    private BackgroundTask createTask(Long fileId) {
        BackgroundTask task = new BackgroundTask();
        task.setId(99L);
        task.setType(BackgroundTaskType.MEDIA_META);
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setUserId(7L);
        task.setPublicStateJson("{\"fileId\":" + fileId + "}");
        task.setPrivateStateJson("{\"fileId\":" + fileId + ",\"taskType\":\"MEDIA_META\"}");
        return task;
    }

    private WorkspaceFileSnapshot createFile(Long id, boolean directory, String contentType, Long size, Long blobId) {
        return new WorkspaceFileSnapshot(
                id,
                7L,
                "media-" + id,
                "/media",
                size,
                contentType,
                directory,
                blobId,
                java.time.LocalDateTime.now()
        );
    }

    private byte[] createPngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
