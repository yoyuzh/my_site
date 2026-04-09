package com.yoyuzh.files.storage;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.config.FileStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3FileContentStorageTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3FileContentStorage storage;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider("s3");
        properties.getS3().setApiAccessKey("doge-ak");
        properties.getS3().setApiSecretKey("doge-sk");
        properties.getS3().setScope("yoyuzh-files");
        properties.getS3().setRegion("automatic");
        storage = new S3FileContentStorage(properties, "demo-bucket", s3Client, s3Presigner);
    }

    @Test
    void prepareUploadCreatesDirectPutUrl() throws Exception {
        PresignedPutObjectRequest presignedRequest = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://upload.example.com/users/7/docs/notes.txt"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        PreparedUpload preparedUpload = storage.prepareUpload(7L, "/docs", "notes.txt", "text/plain", 12L);

        assertThat(preparedUpload.direct()).isTrue();
        assertThat(preparedUpload.method()).isEqualTo("PUT");
        assertThat(preparedUpload.uploadUrl()).isEqualTo("https://upload.example.com/users/7/docs/notes.txt");
        assertThat(preparedUpload.headers()).containsEntry("Content-Type", "text/plain");

        ArgumentCaptor<PutObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(requestCaptor.capture());
        PutObjectRequest putObjectRequest = requestCaptor.getValue().putObjectRequest();
        assertThat(putObjectRequest.bucket()).isEqualTo("demo-bucket");
        assertThat(putObjectRequest.key()).isEqualTo("users/7/docs/notes.txt");
        assertThat(putObjectRequest.contentType()).isEqualTo("text/plain");
    }

    @Test
    void completeUploadRejectsMissingObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertThatThrownBy(() -> storage.completeUpload(7L, "/docs", "notes.txt", "text/plain", 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上传文件不存在");
    }

    @Test
    void createDownloadUrlSignsGetRequestWithDownloadFilename() throws Exception {
        PresignedGetObjectRequest presignedRequest = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://download.example.com/object"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        String url = storage.createDownloadUrl(7L, "/docs", "notes.txt", "读书笔记.txt");

        assertThat(url).isEqualTo("https://download.example.com/object");

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(requestCaptor.capture());
        GetObjectRequest getObjectRequest = requestCaptor.getValue().getObjectRequest();
        assertThat(getObjectRequest.bucket()).isEqualTo("demo-bucket");
        assertThat(getObjectRequest.key()).isEqualTo("users/7/docs/notes.txt");
        assertThat(getObjectRequest.responseContentDisposition())
                .isEqualTo("attachment; filename=\"download.txt\"; filename*=UTF-8''%E8%AF%BB%E4%B9%A6%E7%AC%94%E8%AE%B0.txt");
    }

    @Test
    void createDownloadUrlFallsBackToGenericNameWhenOriginalFilenameHasNoAsciiCharacters() throws Exception {
        PresignedGetObjectRequest presignedRequest = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://download.example.com/object"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        storage.createDownloadUrl(7L, "/docs", "notes.txt", "你好");

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getObjectRequest().responseContentDisposition())
                .isEqualTo("attachment; filename=\"download\"; filename*=UTF-8''%E4%BD%A0%E5%A5%BD");
    }


    @Test
    void uploadStoresMultipartContentInConfiguredBucket() {
        org.springframework.mock.web.MockMultipartFile multipartFile = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello".getBytes()
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("demo").build());

        storage.upload(7L, "/docs", "notes.txt", multipartFile);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("demo-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("users/7/docs/notes.txt");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("text/plain");
    }

    @Test
    void renameFileCopiesThenDeletesSourceObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        storage.renameFile(7L, "/docs", "old.txt", "new.txt");

        ArgumentCaptor<CopyObjectRequest> copyCaptor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copyCaptor.capture());
        assertThat(copyCaptor.getValue().sourceBucket()).isEqualTo("demo-bucket");
        assertThat(copyCaptor.getValue().sourceKey()).isEqualTo("users/7/docs/old.txt");
        assertThat(copyCaptor.getValue().destinationBucket()).isEqualTo("demo-bucket");
        assertThat(copyCaptor.getValue().destinationKey()).isEqualTo("users/7/docs/new.txt");
        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo("demo-bucket");
        assertThat(deleteCaptor.getValue().key()).isEqualTo("users/7/docs/old.txt");
    }

    @Test
    void createMultipartUploadStartsMultipartInConfiguredBucket() {
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-123").build());

        String uploadId = storage.createMultipartUpload("blobs/object-1", "video/mp4");

        assertThat(uploadId).isEqualTo("upload-123");
        ArgumentCaptor<CreateMultipartUploadRequest> requestCaptor = ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);
        verify(s3Client).createMultipartUpload(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("demo-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("blobs/object-1");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("video/mp4");
    }

    @Test
    void prepareMultipartPartUploadSignsUploadPartRequest() throws Exception {
        PresignedUploadPartRequest presignedRequest = org.mockito.Mockito.mock(PresignedUploadPartRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://upload.example.com/blobs/object-1?partNumber=2"));
        when(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class))).thenReturn(presignedRequest);

        PreparedUpload preparedUpload = storage.prepareMultipartPartUpload("blobs/object-1", "upload-123", 2, "video/mp4", 1024L);

        assertThat(preparedUpload.direct()).isTrue();
        assertThat(preparedUpload.method()).isEqualTo("PUT");
        assertThat(preparedUpload.uploadUrl()).contains("partNumber=2");
        ArgumentCaptor<UploadPartPresignRequest> requestCaptor = ArgumentCaptor.forClass(UploadPartPresignRequest.class);
        verify(s3Presigner).presignUploadPart(requestCaptor.capture());
        assertThat(requestCaptor.getValue().uploadPartRequest().bucket()).isEqualTo("demo-bucket");
        assertThat(requestCaptor.getValue().uploadPartRequest().key()).isEqualTo("blobs/object-1");
        assertThat(requestCaptor.getValue().uploadPartRequest().partNumber()).isEqualTo(2);
        assertThat(requestCaptor.getValue().uploadPartRequest().uploadId()).isEqualTo("upload-123");
    }

    @Test
    void completeMultipartUploadSubmitsSortedCompletedParts() {
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().build());

        storage.completeMultipartUpload("blobs/object-1", "upload-123", List.of(
                new MultipartCompletedPart(2, "etag-2"),
                new MultipartCompletedPart(1, "etag-1")
        ));

        ArgumentCaptor<CompleteMultipartUploadRequest> requestCaptor = ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(s3Client).completeMultipartUpload(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("demo-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("blobs/object-1");
        assertThat(requestCaptor.getValue().uploadId()).isEqualTo("upload-123");
        assertThat(requestCaptor.getValue().multipartUpload().parts()).extracting(CompletedPart::partNumber)
                .containsExactly(1, 2);
    }

    @Test
    void abortMultipartUploadCancelsRemoteMultipartState() {
        storage.abortMultipartUpload("blobs/object-1", "upload-123");

        ArgumentCaptor<AbortMultipartUploadRequest> requestCaptor = ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
        verify(s3Client).abortMultipartUpload(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("demo-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("blobs/object-1");
        assertThat(requestCaptor.getValue().uploadId()).isEqualTo("upload-123");
    }

    @Test
    void readFileFallsBackToLegacyObjectKeyWhenNeeded() {
        when(s3Client.headObject(HeadObjectRequest.builder().bucket("demo-bucket").key("users/7/docs/notes.txt").build()))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());
        when(s3Client.headObject(HeadObjectRequest.builder().bucket("demo-bucket").key("7/docs/notes.txt").build()))
                .thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket("demo-bucket").key("7/docs/notes.txt").build()))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "hello".getBytes()));

        byte[] content = storage.readFile(7L, "/docs", "notes.txt");

        assertThat(content).isEqualTo("hello".getBytes());
    }
}
