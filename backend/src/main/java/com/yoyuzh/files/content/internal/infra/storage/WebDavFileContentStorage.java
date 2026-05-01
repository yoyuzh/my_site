package com.yoyuzh.files.content.internal.infra.storage;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.api.PreparedUpload;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class WebDavFileContentStorage implements FileContentStorage, AutoCloseable {

    private final StorageRuntimeProperties.WebDav properties;
    private final Sardine sardine;

    public WebDavFileContentStorage(StorageRuntimeProperties storageProperties) {
        this(
                storageProperties.getWebDav(),
                SardineFactory.begin(
                        storageProperties.getWebDav().getUsername(),
                        storageProperties.getWebDav().getPassword()
                )
        );
    }

    WebDavFileContentStorage(StorageRuntimeProperties.WebDav properties, Sardine sardine) {
        this.properties = properties;
        this.sardine = sardine;
    }

    @Override
    public void close() throws IOException {
        sardine.shutdown();
    }

    @Override
    public PreparedUpload prepareUpload(Long userId, String path, String storageName, String contentType, long size) {
        return new PreparedUpload(false, "", "POST", Map.of(), storageName);
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
        throw new UnsupportedOperationException("WebDAV storage does not support signed direct download URLs");
    }

    @Override
    public PreparedUpload prepareBlobUpload(String path, String filename, String objectKey, String contentType, long size) {
        return new PreparedUpload(false, "", "POST", Map.of(), objectKey);
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
        try {
            DavResource resource = readResource(objectKey);
            if (resource == null) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "上传文件不存在");
            }
            verifyBlobMetadata(resource.getContentLength(), resource.getContentType(), contentType, size);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File content verification failed");
        }
    }

    @Override
    public void storeBlob(String objectKey, String contentType, byte[] content) {
        storeBlob(objectKey, contentType, new java.io.ByteArrayInputStream(content), content.length);
    }

    @Override
    public void storeBlob(String objectKey, String contentType, InputStream content, long size) {
        try (InputStream inputStream = content) {
            ensureParentDirectory(resolveUrl(objectKey));
            sardine.put(resolveUrl(objectKey), inputStream, contentType);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    @Override
    public byte[] readBlob(String objectKey) {
        try (InputStream content = sardine.get(resolveUrl(objectKey))) {
            return content.readAllBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "File content does not exist");
        }
    }

    @Override
    public InputStream readBlobStream(String objectKey) {
        try {
            return new ManagedInputStream(sardine.get(resolveUrl(objectKey)));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "File content does not exist");
        }
    }

    @Override
    public void deleteBlob(String objectKey) {
        try {
            if (sardine.exists(resolveUrl(objectKey))) {
                sardine.delete(resolveUrl(objectKey));
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File delete failed");
        }
    }

    @Override
    public String createBlobDownloadUrl(String objectKey, String filename) {
        return resolveUrl(objectKey) + "?download=" + URLEncoder.encode(filename == null ? "download" : filename, StandardCharsets.UTF_8);
    }

    @Override
    public void createDirectory(Long userId, String logicalPath) {
        ensureDirectory(userId, logicalPath);
    }

    @Override
    public void ensureDirectory(Long userId, String logicalPath) {
        ensureParentDirectory(resolveUrl("users/" + userId + "/" + normalizeRelativePath(logicalPath) + "/.keep"));
    }

    @Override
    public void renameFile(Long userId, String path, String oldStorageName, String newStorageName) {
        try {
            String sourceUrl = resolveUrl(resolveLegacyFileObjectKey(userId, path, oldStorageName));
            String targetUrl = resolveUrl(resolveLegacyFileObjectKey(userId, path, newStorageName));
            ensureParentDirectory(targetUrl);
            sardine.move(sourceUrl, targetUrl);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File rename failed");
        }
    }

    @Override
    public void moveFile(Long userId, String oldPath, String storageName, String newPath) {
        try {
            String sourceUrl = resolveUrl(resolveLegacyFileObjectKey(userId, oldPath, storageName));
            String targetUrl = resolveUrl(resolveLegacyFileObjectKey(userId, newPath, storageName));
            ensureParentDirectory(targetUrl);
            sardine.move(sourceUrl, targetUrl);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File move failed");
        }
    }

    @Override
    public void copyFile(Long userId, String path, String storageName, String targetPath) {
        try {
            String sourceUrl = resolveUrl(resolveLegacyFileObjectKey(userId, path, storageName));
            String targetUrl = resolveUrl(resolveLegacyFileObjectKey(userId, targetPath, storageName));
            ensureParentDirectory(targetUrl);
            sardine.copy(sourceUrl, targetUrl);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File copy failed");
        }
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
        return false;
    }

    @Override
    public String resolveLegacyFileObjectKey(Long userId, String path, String storageName) {
        return "users/" + userId + "/" + joinPath(normalizeRelativePath(path), normalizeName(storageName));
    }

    private String resolveTransferObjectKey(String sessionId, String storageName) {
        return "transfers/" + normalizeName(sessionId) + "/" + normalizeName(storageName);
    }

    private String resolveUrl(String objectKey) {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        String rootPath = normalizeRelativePath(properties.getRootPath());
        String normalizedObjectKey = normalizeRelativePath(objectKey);
        String relative = rootPath.isEmpty() ? normalizedObjectKey : joinPath(rootPath, normalizedObjectKey);
        return baseUrl + "/" + relative;
    }

    private void ensureParentDirectory(String fileUrl) {
        int slashIndex = fileUrl.lastIndexOf('/');
        if (slashIndex <= 0) {
            return;
        }
        String[] parts = fileUrl.substring(0, slashIndex).split("/");
        StringBuilder current = new StringBuilder(parts[0]).append("//").append(parts[2]);
        for (int index = 3; index < parts.length; index += 1) {
            current.append('/').append(parts[index]);
            try {
                if (!sardine.exists(current.toString())) {
                    sardine.createDirectory(current.toString());
                }
            } catch (IOException ex) {
                throw new BusinessException(ErrorCode.UNKNOWN, "Directory create failed");
            }
        }
    }

    private DavResource readResource(String objectKey) throws IOException {
        List<DavResource> resources = sardine.list(resolveUrl(objectKey), 0);
        return resources.isEmpty() ? null : resources.get(0);
    }

    private void verifyBlobMetadata(Long actualSize, String actualContentType, String expectedContentType, long expectedSize) {
        if (actualSize == null) {
            throw new BusinessException(ErrorCode.UNKNOWN, "uploaded file metadata is incomplete");
        }
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

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "WebDAV base URL is not configured");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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

    private String joinPath(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return right;
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }
        return left + "/" + right;
    }

    private static final class ManagedInputStream extends FilterInputStream {
        private ManagedInputStream(InputStream inputStream) {
            super(inputStream);
        }
    }
}
