package com.yoyuzh.files.content.internal.infra.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.UploadPartRequest;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.api.MultipartCompletedPart;
import com.yoyuzh.files.content.api.PreparedUpload;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OssSdkFileContentStorage implements FileContentStorage, AutoCloseable {

    private final StorageRuntimeProperties.Oss properties;
    private final OSS client;

    public OssSdkFileContentStorage(StorageRuntimeProperties storageProperties) {
        this(
                storageProperties.getOss(),
                new OSSClientBuilder().build(
                        storageProperties.getOss().getEndpoint(),
                        storageProperties.getOss().getAccessKeyId(),
                        storageProperties.getOss().getAccessKeySecret()
                )
        );
    }

    OssSdkFileContentStorage(StorageRuntimeProperties.Oss properties, OSS client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public void close() {
        client.shutdown();
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
        return readBlob(resolveLegacyFileObjectKey(userId, path, storageName));
    }

    @Override
    public void deleteFile(Long userId, String path, String storageName) {
        deleteBlob(resolveLegacyFileObjectKey(userId, path, storageName));
    }

    @Override
    public String createDownloadUrl(Long userId, String path, String storageName, String filename) {
        return createBlobDownloadUrl(resolveLegacyFileObjectKey(userId, path, storageName), filename);
    }

    @Override
    public PreparedUpload prepareBlobUpload(String path, String filename, String objectKey, String contentType, long size) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                properties.getBucketName(),
                normalizeObjectKey(objectKey),
                HttpMethod.PUT
        );
        request.setExpiration(expiration());
        if (StringUtils.hasText(contentType)) {
            request.addHeader("Content-Type", contentType);
        }
        URL url = client.generatePresignedUrl(request);
        Map<String, String> headers = new HashMap<>();
        if (StringUtils.hasText(contentType)) {
            headers.put("Content-Type", contentType);
        }
        return new PreparedUpload(true, url.toString(), "PUT", headers, objectKey);
    }

    @Override
    public void uploadBlob(String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            storeBlob(objectKey, file.getContentType(), inputStream, file.getSize());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    @Override
    public void completeBlobUpload(String objectKey, String contentType, long size) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        if (!client.doesObjectExist(properties.getBucketName(), normalizedObjectKey)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "上传文件不存在");
        }
        ObjectMetadata metadata = client.getObjectMetadata(properties.getBucketName(), normalizedObjectKey);
        verifyBlobMetadata(metadata.getContentLength(), metadata.getContentType(), contentType, size);
    }

    @Override
    public void storeBlob(String objectKey, String contentType, byte[] content) {
        storeBlob(objectKey, contentType, new java.io.ByteArrayInputStream(content), content.length);
    }

    @Override
    public void storeBlob(String objectKey, String contentType, InputStream content, long size) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(size);
        if (StringUtils.hasText(contentType)) {
            metadata.setContentType(contentType);
        }
        try (InputStream inputStream = content) {
            client.putObject(properties.getBucketName(), normalizeObjectKey(objectKey), inputStream, metadata);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    @Override
    public byte[] readBlob(String objectKey) {
        try (OSSObject object = client.getObject(new GetObjectRequest(properties.getBucketName(), normalizeObjectKey(objectKey)));
             InputStream content = object.getObjectContent()) {
            return content.readAllBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "File content does not exist");
        }
    }

    @Override
    public InputStream readBlobStream(String objectKey) {
        OSSObject object = client.getObject(new GetObjectRequest(properties.getBucketName(), normalizeObjectKey(objectKey)));
        return new ManagedInputStream(object.getObjectContent(), object);
    }

    @Override
    public boolean supportsDeferredBlobUpload() {
        return true;
    }

    @Override
    public void deleteBlob(String objectKey) {
        client.deleteObject(properties.getBucketName(), normalizeObjectKey(objectKey));
    }

    @Override
    public String createMultipartUpload(String objectKey, String contentType) {
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(
                properties.getBucketName(),
                normalizeObjectKey(objectKey)
        );
        if (StringUtils.hasText(contentType)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            request.setObjectMetadata(metadata);
        }
        return client.initiateMultipartUpload(request).getUploadId();
    }

    @Override
    public PreparedUpload prepareMultipartPartUpload(String objectKey,
                                                     String uploadId,
                                                     int partNumber,
                                                     String contentType,
                                                     long size) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                properties.getBucketName(),
                normalizeObjectKey(objectKey),
                HttpMethod.PUT
        );
        request.setExpiration(expiration());
        request.addQueryParameter("partNumber", String.valueOf(partNumber));
        request.addQueryParameter("uploadId", uploadId);
        if (StringUtils.hasText(contentType)) {
            request.addHeader("Content-Type", contentType);
        }
        URL url = client.generatePresignedUrl(request);
        Map<String, String> headers = new HashMap<>();
        if (StringUtils.hasText(contentType)) {
            headers.put("Content-Type", contentType);
        }
        return new PreparedUpload(true, url.toString(), "PUT", headers, objectKey);
    }

    @Override
    public void completeMultipartUpload(String objectKey, String uploadId, List<MultipartCompletedPart> parts) {
        List<PartETag> partETags = parts.stream()
                .sorted(Comparator.comparingInt(MultipartCompletedPart::partNumber))
                .map(part -> new PartETag(part.partNumber(), part.etag()))
                .toList();
        client.completeMultipartUpload(new CompleteMultipartUploadRequest(
                properties.getBucketName(),
                normalizeObjectKey(objectKey),
                uploadId,
                partETags
        ));
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        client.abortMultipartUpload(new AbortMultipartUploadRequest(
                properties.getBucketName(),
                normalizeObjectKey(objectKey),
                uploadId
        ));
    }

    @Override
    public String createBlobDownloadUrl(String objectKey, String filename) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                properties.getBucketName(),
                normalizeObjectKey(objectKey),
                HttpMethod.GET
        );
        request.setExpiration(expiration());
        if (StringUtils.hasText(filename)) {
            request.addHeader("response-content-disposition", contentDisposition(filename));
        }
        return client.generatePresignedUrl(request).toString();
    }

    @Override
    public void createDirectory(Long userId, String logicalPath) {
    }

    @Override
    public void ensureDirectory(Long userId, String logicalPath) {
    }

    @Override
    public void renameFile(Long userId, String path, String oldStorageName, String newStorageName) {
        copyObject(resolveLegacyFileObjectKey(userId, path, oldStorageName), resolveLegacyFileObjectKey(userId, path, newStorageName));
        deleteBlob(resolveLegacyFileObjectKey(userId, path, oldStorageName));
    }

    @Override
    public void moveFile(Long userId, String oldPath, String storageName, String newPath) {
        copyObject(resolveLegacyFileObjectKey(userId, oldPath, storageName), resolveLegacyFileObjectKey(userId, newPath, storageName));
        deleteBlob(resolveLegacyFileObjectKey(userId, oldPath, storageName));
    }

    @Override
    public void copyFile(Long userId, String path, String storageName, String targetPath) {
        copyObject(resolveLegacyFileObjectKey(userId, path, storageName), resolveLegacyFileObjectKey(userId, targetPath, storageName));
    }

    @Override
    public void storeImportedFile(Long userId, String path, String storageName, String contentType, byte[] content) {
        storeBlob(resolveLegacyFileObjectKey(userId, path, storageName), contentType, content);
    }

    @Override
    public void storeTransferFile(String sessionId, String storageName, String contentType, byte[] content) {
        storeBlob(resolveTransferObjectKey(sessionId, storageName), contentType, content);
    }

    @Override
    public byte[] readTransferFile(String sessionId, String storageName) {
        return readBlob(resolveTransferObjectKey(sessionId, storageName));
    }

    @Override
    public InputStream readTransferFileStream(String sessionId, String storageName) {
        return readBlobStream(resolveTransferObjectKey(sessionId, storageName));
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

    private void copyObject(String sourceKey, String targetKey) {
        client.copyObject(new CopyObjectRequest(
                properties.getBucketName(),
                normalizeObjectKey(sourceKey),
                properties.getBucketName(),
                normalizeObjectKey(targetKey)
        ));
    }

    private String resolveTransferObjectKey(String sessionId, String storageName) {
        return "transfers/" + normalizeName(sessionId) + "/" + normalizeName(storageName);
    }

    private String normalizeObjectKey(String objectKey) {
        String cleaned = org.springframework.util.StringUtils.cleanPath(objectKey == null ? "" : objectKey).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.startsWith("/") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid storage object key");
        }
        String prefix = normalizeRelativePath(properties.getPrefix());
        return prefix.isEmpty() ? cleaned : joinObjectKeyParts(prefix, cleaned);
    }

    private String normalizeRelativePath(String path) {
        String cleaned = org.springframework.util.StringUtils.cleanPath(path == null ? "" : path).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || "/".equals(cleaned)) {
            return "";
        }
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid storage path");
        }
        return cleaned;
    }

    private String normalizeName(String name) {
        String cleaned = org.springframework.util.StringUtils.cleanPath(name == null ? "" : name).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.startsWith("/") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid storage filename");
        }
        return cleaned;
    }

    private String joinObjectKeyParts(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return right;
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }
        return left + "/" + right;
    }

    private Date expiration() {
        return Date.from(Instant.now().plusSeconds(Math.max(1, properties.getTtlSeconds())));
    }

    private String contentDisposition(String filename) {
        return "attachment; filename=\"" + asciiFilenameFallback(filename)
                + "\"; filename*=UTF-8''" + encodeRfc5987(filename);
    }

    private void verifyBlobMetadata(long actualSize, String actualContentType, String expectedContentType, long expectedSize) {
        if (expectedSize >= 0 && actualSize != expectedSize) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "uploaded file size does not match session");
        }
        if (StringUtils.hasText(expectedContentType)
                && !normalizeContentType(expectedContentType).equals(normalizeContentType(actualContentType))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "uploaded file content type does not match session");
        }
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int parameterIndex = contentType.indexOf(';');
        String normalized = parameterIndex >= 0 ? contentType.substring(0, parameterIndex) : contentType;
        return normalized.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String asciiFilenameFallback(String filename) {
        String candidate = filename == null ? "download" : filename;
        StringBuilder builder = new StringBuilder(candidate.length());
        for (int index = 0; index < candidate.length(); index += 1) {
            char current = candidate.charAt(index);
            if (current == '"' || current == '\r' || current == '\n') {
                continue;
            }
            builder.append(current >= 0x20 && current <= 0x7E ? current : '_');
        }
        String cleaned = builder.toString().trim();
        return cleaned.isEmpty() ? "download" : cleaned;
    }

    private String encodeRfc5987(String value) {
        String candidate = value == null ? "download" : value;
        byte[] bytes = candidate.getBytes(StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(bytes.length * 3);
        for (byte current : bytes) {
            int unsigned = current & 0xFF;
            if (isRfc5987AttrChar(unsigned)) {
                builder.append((char) unsigned);
                continue;
            }
            builder.append('%');
            builder.append(Character.toUpperCase(Character.forDigit((unsigned >>> 4) & 0xF, 16)));
            builder.append(Character.toUpperCase(Character.forDigit(unsigned & 0xF, 16)));
        }
        return builder.toString();
    }

    private boolean isRfc5987AttrChar(int value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9')
                || value == '!'
                || value == '#'
                || value == '$'
                || value == '&'
                || value == '+'
                || value == '-'
                || value == '.'
                || value == '^'
                || value == '_'
                || value == '`'
                || value == '|'
                || value == '~';
    }

    private static final class ManagedInputStream extends FilterInputStream {
        private final OSSObject object;

        private ManagedInputStream(InputStream inputStream, OSSObject object) {
            super(inputStream);
            this.object = object;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                super.close();
            } catch (IOException ex) {
                failure = ex;
            }
            try {
                object.close();
            } catch (IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
