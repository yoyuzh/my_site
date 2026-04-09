package com.yoyuzh.files.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.search.FileMetadata;
import com.yoyuzh.files.search.FileMetadataRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaMetadataBackgroundTaskHandlerTest {

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileMetadataRepository fileMetadataRepository;
    @Mock
    private FileContentStorage fileContentStorage;

    private MediaMetadataBackgroundTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MediaMetadataBackgroundTaskHandler(
                storedFileRepository,
                fileMetadataRepository,
                fileContentStorage,
                new ObjectMapper()
        );
    }

    @Test
    void shouldExtractImageMetadataFromPngBlob() throws Exception {
        BackgroundTask task = createTask(11L);
        StoredFile file = createFile(11L, false, "image/png", 64L, "blobs/photo.png");
        byte[] pngBytes = createPngBytes(2, 1);

        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(11L, 7L)).thenReturn(Optional.of(file));
        when(fileContentStorage.readBlob("blobs/photo.png")).thenReturn(pngBytes);
        when(fileMetadataRepository.findByFileIdAndName(11L, "media:contentType")).thenReturn(Optional.empty());
        when(fileMetadataRepository.findByFileIdAndName(11L, "media:size")).thenReturn(Optional.empty());
        when(fileMetadataRepository.findByFileIdAndName(11L, "media:width")).thenReturn(Optional.empty());
        when(fileMetadataRepository.findByFileIdAndName(11L, "media:height")).thenReturn(Optional.empty());
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTaskHandlerResult result = handler.handle(task);

        assertThat(result.publicStatePatch()).containsEntry("worker", "media-metadata");
        assertThat(result.publicStatePatch()).containsEntry("metadataExtracted", true);
        assertThat(result.publicStatePatch()).containsEntry("mediaContentType", "image/png");
        assertThat(result.publicStatePatch()).containsEntry("mediaSize", 64L);
        assertThat(result.publicStatePatch()).containsEntry("mediaWidth", 2);
        assertThat(result.publicStatePatch()).containsEntry("mediaHeight", 1);
        verify(fileContentStorage).readBlob("blobs/photo.png");

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        verify(fileMetadataRepository, times(4)).save(captor.capture());
        List<FileMetadata> saved = captor.getAllValues();
        assertThat(saved).extracting(FileMetadata::getName)
                .containsExactly("media:contentType", "media:size", "media:width", "media:height");
        assertThat(saved).extracting(FileMetadata::getValue)
                .containsExactly("image/png", "64", "2", "1");
    }

    @Test
    void shouldWriteBaseMetadataForVideoBlobWithoutDimensions() {
        BackgroundTask task = createTask(12L);
        StoredFile file = createFile(12L, false, "video/mp4", 128L, "blobs/movie.mp4");

        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(12L, 7L)).thenReturn(Optional.of(file));
        when(fileContentStorage.readBlob("blobs/movie.mp4")).thenReturn(new byte[] {0, 1, 2});
        when(fileMetadataRepository.findByFileIdAndName(12L, "media:contentType")).thenReturn(Optional.empty());
        when(fileMetadataRepository.findByFileIdAndName(12L, "media:size")).thenReturn(Optional.empty());
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTaskHandlerResult result = handler.handle(task);

        assertThat(result.publicStatePatch()).containsEntry("worker", "media-metadata");
        assertThat(result.publicStatePatch()).containsEntry("metadataExtracted", true);
        assertThat(result.publicStatePatch()).containsEntry("mediaContentType", "video/mp4");
        assertThat(result.publicStatePatch()).containsEntry("mediaSize", 128L);
        assertThat(result.publicStatePatch()).doesNotContainKeys("mediaWidth", "mediaHeight");
        verify(fileMetadataRepository, times(2)).save(any(FileMetadata.class));
    }

    @Test
    void shouldRejectMissingFileDirectoryOrBlob() {
        BackgroundTask task = createTask(13L);

        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(13L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("media metadata task file not found");

        StoredFile directory = createFile(13L, true, null, 0L, null);
        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(13L, 7L)).thenReturn(Optional.of(directory));
        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("media metadata task only supports files");

        StoredFile missingBlob = createFile(13L, false, "image/png", 10L, null);
        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(13L, 7L)).thenReturn(Optional.of(missingBlob));
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

    private StoredFile createFile(Long id, boolean directory, String contentType, Long size, String objectKey) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setDirectory(directory);
        file.setContentType(contentType);
        file.setSize(size);
        if (objectKey != null) {
            FileBlob blob = new FileBlob();
            blob.setId(100L);
            blob.setObjectKey(objectKey);
            blob.setContentType(contentType);
            blob.setSize(size);
            file.setBlob(blob);
        }
        return file;
    }

    private byte[] createPngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
