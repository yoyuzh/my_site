package com.yoyuzh.files.content.internal.infra.storage;

import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.api.PreparedUpload;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class LocalFileContentStorage implements FileContentStorage {

    private final Path rootPath;

    public LocalFileContentStorage(StorageRuntimeProperties properties) {
        this.rootPath = Path.of(properties.getLocal().getRootDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize local storage root", ex);
        }
    }

    @Override
    public PreparedUpload prepareUpload(Long userId, String path, String storageName, String contentType, long size) {
        return new PreparedUpload(false, "", "POST", Map.of(), storageName);
    }

    @Override
    public void upload(Long userId, String path, String storageName, MultipartFile file) {
        write(resolveLegacyPath(userId, path, storageName), file);
    }

    @Override
    public void completeUpload(Long userId, String path, String storageName, String contentType, long size) {
        ensureReadable(resolveLegacyPath(userId, path, storageName));
    }

    @Override
    public byte[] readFile(Long userId, String path, String storageName) {
        return read(resolveLegacyPath(userId, path, storageName));
    }

    @Override
    public void deleteFile(Long userId, String path, String storageName) {
        delete(resolveLegacyPath(userId, path, storageName));
    }

    @Override
    public String createDownloadUrl(Long userId, String path, String storageName, String filename) {
        throw new UnsupportedOperationException("Local storage does not support direct download URLs");
    }

    @Override
    public PreparedUpload prepareBlobUpload(String path, String filename, String objectKey, String contentType, long size) {
        return new PreparedUpload(false, "", "POST", Map.of(), objectKey);
    }

    @Override
    public void uploadBlob(String objectKey, MultipartFile file) {
        write(resolveObjectKey(objectKey), file);
    }

    @Override
    public void completeBlobUpload(String objectKey, String contentType, long size) {
        ensureReadable(resolveObjectKey(objectKey));
    }

    @Override
    public void storeBlob(String objectKey, String contentType, byte[] content) {
        write(resolveObjectKey(objectKey), content);
    }

    @Override
    public void storeBlob(String objectKey, String contentType, InputStream content, long size) {
        write(resolveObjectKey(objectKey), content);
    }

    @Override
    public byte[] readBlob(String objectKey) {
        return read(resolveObjectKey(objectKey));
    }

    @Override
    public InputStream readBlobStream(String objectKey) {
        return readStream(resolveObjectKey(objectKey));
    }

    @Override
    public void deleteBlob(String objectKey) {
        delete(resolveObjectKey(objectKey));
    }

    @Override
    public String createBlobDownloadUrl(String objectKey, String filename) {
        throw new UnsupportedOperationException("Local storage does not support direct download URLs");
    }

    @Override
    public void createDirectory(Long userId, String logicalPath) {
        ensureDirectory(userId, logicalPath);
    }

    @Override
    public void ensureDirectory(Long userId, String logicalPath) {
        createDirectories(resolveUserDirectory(userId, logicalPath));
    }

    @Override
    public void storeTransferFile(String sessionId, String storageName, String contentType, byte[] content) {
        write(resolveTransferPath(sessionId, storageName), content);
    }

    @Override
    public byte[] readTransferFile(String sessionId, String storageName) {
        return read(resolveTransferPath(sessionId, storageName));
    }

    @Override
    public InputStream readTransferFileStream(String sessionId, String storageName) {
        return readStream(resolveTransferPath(sessionId, storageName));
    }

    @Override
    public void deleteTransferFile(String sessionId, String storageName) {
        delete(resolveTransferPath(sessionId, storageName));
    }

    @Override
    public String createTransferDownloadUrl(String sessionId, String storageName, String filename) {
        throw new UnsupportedOperationException("Local storage does not support direct download URLs");
    }

    @Override
    public boolean supportsDirectDownload() {
        return false;
    }

    @Override
    public String resolveLegacyFileObjectKey(Long userId, String path, String storageName) {
        return "users/" + userId + "/" + normalizeRelativePath(path) + "/" + normalizeName(storageName);
    }

    private Path resolveLegacyPath(Long userId, String path, String storageName) {
        return resolveObjectKey(resolveLegacyFileObjectKey(userId, path, storageName));
    }

    private Path resolveTransferPath(String sessionId, String storageName) {
        return resolveObjectKey("transfers/" + normalizeName(sessionId) + "/" + normalizeName(storageName));
    }

    private Path resolveUserDirectory(Long userId, String logicalPath) {
        return resolveObjectKey("users/" + userId + "/" + normalizeRelativePath(logicalPath));
    }

    private Path resolveObjectKey(String objectKey) {
        Path resolved = rootPath.resolve(normalizeObjectKey(objectKey)).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid storage path");
        }
        return resolved;
    }

    private String normalizeObjectKey(String objectKey) {
        String cleaned = StringUtils.cleanPath(objectKey == null ? "" : objectKey).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.startsWith("/") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid storage object key");
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
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid storage path");
        }
        return cleaned;
    }

    private String normalizeName(String name) {
        String cleaned = StringUtils.cleanPath(name == null ? "" : name).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.startsWith("/") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid storage filename");
        }
        return cleaned;
    }

    private void write(Path target, MultipartFile file) {
        try {
            createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    private void write(Path target, byte[] content) {
        try {
            createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    private void write(Path target, InputStream content) {
        try (InputStream inputStream = content) {
            createDirectories(target.getParent());
            Files.copy(inputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    private byte[] read(Path target) {
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "File content does not exist");
        }
    }

    private InputStream readStream(Path target) {
        try {
            return Files.newInputStream(target, StandardOpenOption.READ);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "File content does not exist");
        }
    }

    private void delete(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File delete failed");
        }
    }

    private void ensureReadable(Path target) {
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "File content does not exist");
        }
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Directory create failed");
        }
    }
}
