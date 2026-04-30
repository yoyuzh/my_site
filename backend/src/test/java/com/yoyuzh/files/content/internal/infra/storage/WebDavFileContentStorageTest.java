package com.yoyuzh.files.content.internal.infra.storage;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebDavFileContentStorageTest {

    @Test
    void shouldRejectCompletedBlobWhenWebDavMetadataDoesNotMatchSessionSize() throws Exception {
        FileStorageProperties.WebDav properties = new FileStorageProperties.WebDav();
        properties.setBaseUrl("https://dav.example.com");
        Sardine sardine = mock(Sardine.class);
        DavResource resource = mock(DavResource.class);
        when(resource.getContentLength()).thenReturn(8L);
        when(resource.getContentType()).thenReturn("video/mp4");
        when(sardine.list("https://dav.example.com/blobs/demo", 0)).thenReturn(List.of(resource));

        WebDavFileContentStorage storage = new WebDavFileContentStorage(properties, sardine);

        assertThatThrownBy(() -> storage.completeBlobUpload("blobs/demo", "video/mp4", 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size does not match session");
    }

    @Test
    void shouldCloseReturnedWebDavBlobStream() throws Exception {
        FileStorageProperties.WebDav properties = new FileStorageProperties.WebDav();
        properties.setBaseUrl("https://dav.example.com");
        Sardine sardine = mock(Sardine.class);
        InputStream payload = mock(InputStream.class);
        when(sardine.get("https://dav.example.com/blobs/demo")).thenReturn(payload);

        WebDavFileContentStorage storage = new WebDavFileContentStorage(properties, sardine);

        try (InputStream ignored = storage.readBlobStream("blobs/demo")) {
            // close via try-with-resources
        }

        verify(payload).close();
    }

    @Test
    void shouldShutdownSardineClientWhenStorageCloses() throws Exception {
        FileStorageProperties.WebDav properties = new FileStorageProperties.WebDav();
        properties.setBaseUrl("https://dav.example.com");
        Sardine sardine = mock(Sardine.class);

        WebDavFileContentStorage storage = new WebDavFileContentStorage(properties, sardine);
        storage.close();

        verify(sardine).shutdown();
    }
}
