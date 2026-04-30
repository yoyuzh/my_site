package com.yoyuzh.files.content.internal.infra.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssSdkFileContentStorageTest {

    @Test
    void shouldCloseUnderlyingOssObjectWhenBlobStreamCloses() throws Exception {
        FileStorageProperties.Oss properties = new FileStorageProperties.Oss();
        properties.setBucketName("bucket");
        OSS client = mock(OSS.class);
        OSSObject object = mock(OSSObject.class);
        InputStream payload = mock(InputStream.class);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(object);
        when(object.getObjectContent()).thenReturn(payload);

        OssSdkFileContentStorage storage = new OssSdkFileContentStorage(properties, client);

        try (InputStream ignored = storage.readBlobStream("blobs/demo")) {
            // close via try-with-resources
        }

        verify(payload).close();
        verify(object).close();
    }

    @Test
    void shouldRejectCompletedBlobWhenOssMetadataDoesNotMatchSessionSize() {
        FileStorageProperties.Oss properties = new FileStorageProperties.Oss();
        properties.setBucketName("bucket");
        OSS client = mock(OSS.class);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(8L);
        metadata.setContentType("video/mp4");
        when(client.doesObjectExist("bucket", "blobs/demo")).thenReturn(true);
        when(client.getObjectMetadata("bucket", "blobs/demo")).thenReturn(metadata);

        OssSdkFileContentStorage storage = new OssSdkFileContentStorage(properties, client);

        assertThatThrownBy(() -> storage.completeBlobUpload("blobs/demo", "video/mp4", 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size does not match session");
    }

    @Test
    void shouldUseConfiguredOssTtlWhenPreparingUpload() throws Exception {
        FileStorageProperties.Oss properties = new FileStorageProperties.Oss();
        properties.setBucketName("bucket");
        properties.setTtlSeconds(5);
        OSS client = mock(OSS.class);
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL("https://oss.example.com/upload"));

        OssSdkFileContentStorage storage = new OssSdkFileContentStorage(properties, client);
        storage.prepareBlobUpload("/docs", "movie.mp4", "blobs/demo", "video/mp4", 7L);

        ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(client).generatePresignedUrl(requestCaptor.capture());
        long deltaMillis = requestCaptor.getValue().getExpiration().getTime() - System.currentTimeMillis();
        assertThat(deltaMillis).isBetween(1_000L, 10_000L);
    }
}
