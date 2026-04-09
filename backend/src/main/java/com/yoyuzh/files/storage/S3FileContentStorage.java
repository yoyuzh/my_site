package com.yoyuzh.files.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.FileStorageProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class S3FileContentStorage implements FileContentStorage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FileStorageProperties.S3 properties;
    private final S3SessionProvider sessionProvider;

    public S3FileContentStorage(FileStorageProperties storageProperties) {
        this(
                storageProperties,
                new DogeCloudS3SessionProvider(
                        storageProperties.getS3(),
                        new DogeCloudTmpTokenClient(storageProperties.getS3(), OBJECT_MAPPER)
                )
        );
    }

    S3FileContentStorage(FileStorageProperties storageProperties,
                         String bucket,
                         software.amazon.awssdk.services.s3.S3Client s3Client,
                         software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner) {
        this(storageProperties, () -> new S3FileRuntimeSession(bucket, s3Client, s3Presigner));
    }

    S3FileContentStorage(FileStorageProperties storageProperties, S3SessionProvider sessionProvider) {
        this.properties = storageProperties.getS3();
        this.sessionProvider = sessionProvider;
    }

    @Override
    public PreparedUpload prepareUpload(Long userId, String path, String storageName, String contentType, long size) {
        return prepareBlobUpload(path, storageName, resolveLegacyFileObjectKey(userId, path, storageName), contentType, size);
    }

    @Override
    public void upload(Long userId, String path, String storageName, MultipartFile file) {
        uploadBlob(resolveLegacyFileObjectKey(userId, path, storageName), file);
    }

    @Override
    public void completeUpload(Long userId, String path, String storageName, String contentType, long size) {
        completeBlobUpload(resolveLegacyFileObjectKey(userId, path, storageName), contentType, size);
    }

    @Override
    public byte[] readFile(Long userId, String path, String storageName) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        String objectKey = resolveExistingFileObjectKey(session, userId, path, storageName);
        return readObject(session, objectKey);
    }

    @Override
    public void deleteFile(Long userId, String path, String storageName) {
        deleteBlob(resolveLegacyFileObjectKey(userId, path, storageName));
    }

    @Override
    public String createDownloadUrl(Long userId, String path, String storageName, String filename) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        String objectKey = resolveExistingFileObjectKey(session, userId, path, storageName);
        return createDownloadUrl(session, objectKey, filename);
    }

    @Override
    public PreparedUpload prepareBlobUpload(String path, String filename, String objectKey, String contentType, long size) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(session.bucket())
                .key(normalizeObjectKey(objectKey));
        if (StringUtils.hasText(contentType)) {
            requestBuilder.contentType(contentType);
        }

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(Math.max(1, properties.getTtlSeconds())))
                .putObjectRequest(requestBuilder.build())
                .build();
        PresignedPutObjectRequest presignedRequest = session.s3Presigner().presignPutObject(presignRequest);
        return new PreparedUpload(
                true,
                presignedRequest.url().toString(),
                resolveUploadMethod(presignedRequest),
                resolveUploadHeaders(presignedRequest, contentType),
                objectKey
        );
    }

    @Override
    public void uploadBlob(String objectKey, MultipartFile file) {
        try {
            putObject(objectKey, file.getContentType(), file.getBytes());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    @Override
    public void completeBlobUpload(String objectKey, String contentType, long size) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        try {
            ensureObjectExists(session, normalizeObjectKey(objectKey));
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "上传文件不存在");
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File content verification failed");
        }
    }

    @Override
    public void storeBlob(String objectKey, String contentType, byte[] content) {
        putObject(objectKey, contentType, content);
    }

    @Override
    public byte[] readBlob(String objectKey) {
        return readObject(sessionProvider.currentSession(), normalizeObjectKey(objectKey));
    }

    @Override
    public void deleteBlob(String objectKey) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        try {
            session.s3Client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(session.bucket())
                    .key(normalizeObjectKey(objectKey))
                    .build());
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File delete failed");
        }
    }

    @Override
    public String createMultipartUpload(String objectKey, String contentType) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        CreateMultipartUploadRequest.Builder requestBuilder = CreateMultipartUploadRequest.builder()
                .bucket(session.bucket())
                .key(normalizeObjectKey(objectKey));
        if (StringUtils.hasText(contentType)) {
            requestBuilder.contentType(contentType);
        }
        try {
            return session.s3Client().createMultipartUpload(requestBuilder.build()).uploadId();
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Multipart upload init failed");
        }
    }

    @Override
    public PreparedUpload prepareMultipartPartUpload(String objectKey,
                                                     String uploadId,
                                                     int partNumber,
                                                     String contentType,
                                                     long size) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(session.bucket())
                .key(normalizeObjectKey(objectKey))
                .uploadId(uploadId)
                .partNumber(partNumber)
                .contentLength(size)
                .build();
        UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(Math.max(1, properties.getTtlSeconds())))
                .uploadPartRequest(uploadPartRequest)
                .build();
        PresignedUploadPartRequest presignedRequest = session.s3Presigner().presignUploadPart(presignRequest);
        Map<String, String> headers = flattenSignedHeaders(presignedRequest.signedHeaders());
        if (StringUtils.hasText(contentType)) {
            headers.put("Content-Type", contentType);
        }
        return new PreparedUpload(
                true,
                presignedRequest.url().toString(),
                resolveUploadMethod(presignedRequest),
                headers,
                objectKey
        );
    }

    @Override
    public void completeMultipartUpload(String objectKey, String uploadId, List<MultipartCompletedPart> parts) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        List<CompletedPart> completedParts = parts.stream()
                .sorted(Comparator.comparingInt(MultipartCompletedPart::partNumber))
                .map(part -> CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(part.etag())
                        .build())
                .toList();
        try {
            session.s3Client().completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(session.bucket())
                    .key(normalizeObjectKey(objectKey))
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build());
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Multipart upload complete failed");
        }
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        try {
            session.s3Client().abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(session.bucket())
                    .key(normalizeObjectKey(objectKey))
                    .uploadId(uploadId)
                    .build());
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Multipart upload abort failed");
        }
    }

    @Override
    public String createBlobDownloadUrl(String objectKey, String filename) {
        return createDownloadUrl(sessionProvider.currentSession(), normalizeObjectKey(objectKey), filename);
    }

    @Override
    public void createDirectory(Long userId, String logicalPath) {
    }

    @Override
    public void ensureDirectory(Long userId, String logicalPath) {
    }

    @Override
    public void renameFile(Long userId, String path, String oldStorageName, String newStorageName) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        String sourceKey = resolveExistingFileObjectKey(session, userId, path, oldStorageName);
        String targetKey = resolveLegacyFileObjectKey(userId, path, newStorageName);
        copyObject(session, sourceKey, targetKey);
        deleteObject(session, sourceKey);
    }

    @Override
    public void moveFile(Long userId, String oldPath, String storageName, String newPath) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        String sourceKey = resolveExistingFileObjectKey(session, userId, oldPath, storageName);
        String targetKey = resolveLegacyFileObjectKey(userId, newPath, storageName);
        copyObject(session, sourceKey, targetKey);
        deleteObject(session, sourceKey);
    }

    @Override
    public void copyFile(Long userId, String path, String storageName, String targetPath) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        String sourceKey = resolveExistingFileObjectKey(session, userId, path, storageName);
        String targetKey = resolveLegacyFileObjectKey(userId, targetPath, storageName);
        copyObject(session, sourceKey, targetKey);
    }

    @Override
    public void storeImportedFile(Long userId, String path, String storageName, String contentType, byte[] content) {
        storeBlob(resolveLegacyFileObjectKey(userId, path, storageName), contentType, content);
    }

    @Override
    public void storeTransferFile(String sessionId, String storageName, String contentType, byte[] content) {
        putObject(resolveTransferObjectKey(sessionId, storageName), contentType, content);
    }

    @Override
    public byte[] readTransferFile(String sessionId, String storageName) {
        return readBlob(resolveTransferObjectKey(sessionId, storageName));
    }

    @Override
    public void deleteTransferFile(String sessionId, String storageName) {
        deleteBlob(resolveTransferObjectKey(sessionId, storageName));
    }

    @Override
    public String createTransferDownloadUrl(String sessionId, String storageName, String filename) {
        return createBlobDownloadUrl(resolveTransferObjectKey(sessionId, storageName), filename);
    }

    @Override
    public boolean supportsDirectDownload() {
        return true;
    }

    @Override
    public String resolveLegacyFileObjectKey(Long userId, String path, String storageName) {
        return "users/" + userId + "/" + joinObjectKeyParts(normalizeRelativePath(path), normalizeName(storageName));
    }

    private String resolveExistingFileObjectKey(S3FileRuntimeSession session, Long userId, String path, String storageName) {
        String currentKey = resolveLegacyFileObjectKey(userId, path, storageName);
        try {
            ensureObjectExists(session, currentKey);
            return currentKey;
        } catch (NoSuchKeyException ex) {
            String legacyKey = userId + "/" + joinObjectKeyParts(normalizeRelativePath(path), normalizeName(storageName));
            ensureObjectExists(session, legacyKey);
            return legacyKey;
        }
    }

    private void putObject(String objectKey, String contentType, byte[] content) {
        S3FileRuntimeSession session = sessionProvider.currentSession();
        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(session.bucket())
                .key(normalizeObjectKey(objectKey));
        if (StringUtils.hasText(contentType)) {
            requestBuilder.contentType(contentType);
        }

        try {
            session.s3Client().putObject(requestBuilder.build(), RequestBody.fromBytes(content));
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    private byte[] readObject(S3FileRuntimeSession session, String objectKey) {
        try {
            ResponseBytes<?> response = session.s3Client().getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(session.bucket())
                    .key(normalizeObjectKey(objectKey))
                    .build());
            return response.asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "File content does not exist");
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File read failed");
        }
    }

    private String createDownloadUrl(S3FileRuntimeSession session, String objectKey, String filename) {
        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                .bucket(session.bucket())
                .key(normalizeObjectKey(objectKey));
        if (StringUtils.hasText(filename)) {
            requestBuilder.responseContentDisposition(createContentDisposition(filename));
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(Math.max(1, properties.getTtlSeconds())))
                .getObjectRequest(requestBuilder.build())
                .build();
        PresignedGetObjectRequest presignedRequest = session.s3Presigner().presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    private void copyObject(S3FileRuntimeSession session, String sourceKey, String targetKey) {
        try {
            session.s3Client().copyObject(CopyObjectRequest.builder()
                    .sourceBucket(session.bucket())
                    .sourceKey(normalizeObjectKey(sourceKey))
                    .destinationBucket(session.bucket())
                    .destinationKey(normalizeObjectKey(targetKey))
                    .build());
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File copy failed");
        }
    }

    private void deleteObject(S3FileRuntimeSession session, String objectKey) {
        try {
            session.s3Client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(session.bucket())
                    .key(normalizeObjectKey(objectKey))
                    .build());
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File delete failed");
        }
    }

    private void ensureObjectExists(S3FileRuntimeSession session, String objectKey) {
        session.s3Client().headObject(HeadObjectRequest.builder()
                .bucket(session.bucket())
                .key(normalizeObjectKey(objectKey))
                .build());
    }

    private String resolveUploadMethod(PresignedPutObjectRequest presignedRequest) {
        if (presignedRequest.httpRequest() == null) {
            return "PUT";
        }
        return presignedRequest.httpRequest().method() == SdkHttpMethod.PUT ? "PUT" : "POST";
    }

    private String resolveUploadMethod(PresignedUploadPartRequest presignedRequest) {
        if (presignedRequest.httpRequest() == null) {
            return "PUT";
        }
        return presignedRequest.httpRequest().method() == SdkHttpMethod.PUT ? "PUT" : presignedRequest.httpRequest().method().name();
    }

    private Map<String, String> resolveUploadHeaders(PresignedPutObjectRequest presignedRequest, String contentType) {
        Map<String, String> headers = flattenSignedHeaders(presignedRequest.signedHeaders());
        if (StringUtils.hasText(contentType)) {
            headers.put("Content-Type", contentType);
        }
        return headers;
    }

    private Map<String, String> flattenSignedHeaders(Map<String, List<String>> signedHeaders) {
        Map<String, String> flattened = new HashMap<>();
        if (signedHeaders == null) {
            return flattened;
        }
        signedHeaders.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                flattened.put(key, String.join(",", values));
            }
        });
        return flattened;
    }

    private String createContentDisposition(String filename) {
        return "attachment; filename=\"" + createAsciiFallbackFilename(filename)
                + "\"; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String createAsciiFallbackFilename(String filename) {
        String fallback = "download";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            String extension = filename.substring(dotIndex);
            if (isSafeAsciiToken(extension)) {
                fallback += extension;
            }
        }
        return fallback;
    }

    private boolean isSafeAsciiToken(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < 33 || current > 126 || current == '"' || current == '\\' || current == ';') {
                return false;
            }
        }
        return true;
    }

    private String resolveTransferObjectKey(String sessionId, String storageName) {
        return "transfers/" + normalizeName(sessionId) + "/" + normalizeName(storageName);
    }

    private String joinObjectKeyParts(String path, String storageName) {
        return StringUtils.hasText(path) ? path + "/" + storageName : storageName;
    }

    private String normalizeObjectKey(String objectKey) {
        String cleaned = StringUtils.cleanPath(objectKey == null ? "" : objectKey).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.startsWith("/") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Invalid storage object key");
        }
        return cleaned;
    }

    private String normalizeRelativePath(String path) {
        String cleaned = StringUtils.cleanPath(path == null ? "" : path).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || "/".equals(cleaned)) {
            return "";
        }
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Invalid storage path");
        }
        return cleaned;
    }

    private String normalizeName(String name) {
        String cleaned = StringUtils.cleanPath(name == null ? "" : name).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.startsWith("/") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Invalid storage filename");
        }
        return cleaned;
    }
}
