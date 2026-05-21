package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobReadResult;
import com.yoyuzh.files.content.api.ContentBlobStateView;
import com.yoyuzh.files.content.api.FileBlobStatus;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeContentBlobReadApiTest {

    @Test
    void shouldReadReadyBlobFromStorage() throws Exception {
        ContentBlobQueryApi queryApi = mock(ContentBlobQueryApi.class);
        FileContentStorage storage = mock(FileContentStorage.class);
        when(queryApi.findBlobStateById(1L)).thenReturn(Optional.of(new ContentBlobStateView(
                1L, "blobs/1", "text/plain", 5L, FileBlobStatus.READY, null, null
        )));
        when(storage.readBlobStream("blobs/1")).thenReturn(new ByteArrayInputStream("hello".getBytes()));

        RuntimeContentBlobReadApi api = new RuntimeContentBlobReadApi(queryApi, storage);
        ContentBlobReadResult result = api.readBlob(1L, false);

        assertThat(new String(result.content().readAllBytes())).isEqualTo("hello");
        assertThat(result.pendingFallback()).isFalse();
    }

    @Test
    void shouldReadPendingBlobFromTempFile() throws Exception {
        Path tempFile = Files.createTempFile("pending-blob-read", ".tmp");
        Files.writeString(tempFile, "pending");
        ContentBlobQueryApi queryApi = mock(ContentBlobQueryApi.class);
        FileContentStorage storage = mock(FileContentStorage.class);
        when(queryApi.findBlobStateById(2L)).thenReturn(Optional.of(new ContentBlobStateView(
                2L, "blobs/2", "text/plain", 7L, FileBlobStatus.PENDING, tempFile.toString(), 99L
        )));

        RuntimeContentBlobReadApi api = new RuntimeContentBlobReadApi(queryApi, storage);
        ContentBlobReadResult result = api.readBlob(2L, false);

        assertThat(new String(result.content().readAllBytes())).isEqualTo("pending");
        assertThat(result.pendingFallback()).isTrue();
        Files.deleteIfExists(tempFile);
    }

    @Test
    void shouldRejectFailedOrMissingPendingBlob() {
        ContentBlobQueryApi queryApi = mock(ContentBlobQueryApi.class);
        FileContentStorage storage = mock(FileContentStorage.class);
        when(queryApi.findBlobStateById(3L)).thenReturn(Optional.of(new ContentBlobStateView(
                3L, "blobs/3", "text/plain", 1L, FileBlobStatus.FAILED, null, 100L
        )));

        RuntimeContentBlobReadApi api = new RuntimeContentBlobReadApi(queryApi, storage);

        assertThatThrownBy(() -> api.readBlob(3L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件内容暂不可用");
    }
}
