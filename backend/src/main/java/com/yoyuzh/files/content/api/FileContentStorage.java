package com.yoyuzh.files.content.api;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public interface FileContentStorage {

    PreparedUpload prepareUpload(Long userId, String path, String storageName, String contentType, long size);

    void upload(Long userId, String path, String storageName, MultipartFile file);

    void completeUpload(Long userId, String path, String storageName, String contentType, long size);

    byte[] readFile(Long userId, String path, String storageName);

    void deleteFile(Long userId, String path, String storageName);

    String createDownloadUrl(Long userId, String path, String storageName, String filename);

    @Deprecated
    default void renameFile(Long userId, String path, String oldStorageName, String newStorageName) {
        throw new UnsupportedOperationException("File content rename is not supported by this storage");
    }

    default void renameDirectory(Long userId, String oldPath, String oldStorageName, String newStorageName) {
        throw new UnsupportedOperationException("Directory content rename is not supported by this storage");
    }

    @Deprecated
    default void moveFile(Long userId, String oldPath, String storageName, String newPath) {
        throw new UnsupportedOperationException("File content move is not supported by this storage");
    }

    @Deprecated
    default void copyFile(Long userId, String path, String storageName, String targetPath) {
        throw new UnsupportedOperationException("File content copy is not supported by this storage");
    }

    default void storeImportedFile(Long userId, String path, String storageName, String contentType, byte[] content) {
        throw new UnsupportedOperationException("Imported file storage is not supported by this storage");
    }

    PreparedUpload prepareBlobUpload(String path, String filename, String objectKey, String contentType, long size);

    void uploadBlob(String objectKey, MultipartFile file);

    void completeBlobUpload(String objectKey, String contentType, long size);

    void storeBlob(String objectKey, String contentType, byte[] content);

    default void storeBlob(String objectKey, String contentType, InputStream content, long size) {
        try {
            // Storage adapters should override this to stream large blobs instead of buffering them fully in memory.
            storeBlob(objectKey, contentType, content.readAllBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read blob content stream", ex);
        }
    }

    byte[] readBlob(String objectKey);

    default InputStream readBlobStream(String objectKey) {
        return new ByteArrayInputStream(readBlob(objectKey));
    }

    void deleteBlob(String objectKey);

    default String createMultipartUpload(String objectKey, String contentType) {
        throw new UnsupportedOperationException("Multipart upload is not supported by this storage");
    }

    default PreparedUpload prepareMultipartPartUpload(String objectKey,
                                                      String uploadId,
                                                      int partNumber,
                                                      String contentType,
                                                      long size) {
        throw new UnsupportedOperationException("Multipart upload is not supported by this storage");
    }

    default void completeMultipartUpload(String objectKey, String uploadId, java.util.List<MultipartCompletedPart> parts) {
        throw new UnsupportedOperationException("Multipart upload is not supported by this storage");
    }

    default void abortMultipartUpload(String objectKey, String uploadId) {
        throw new UnsupportedOperationException("Multipart upload is not supported by this storage");
    }

    String createBlobDownloadUrl(String objectKey, String filename);

    void createDirectory(Long userId, String logicalPath);

    void ensureDirectory(Long userId, String logicalPath);

    void storeTransferFile(String sessionId, String storageName, String contentType, byte[] content);

    byte[] readTransferFile(String sessionId, String storageName);

    default InputStream readTransferFileStream(String sessionId, String storageName) {
        return new ByteArrayInputStream(readTransferFile(sessionId, storageName));
    }

    void deleteTransferFile(String sessionId, String storageName);

    String createTransferDownloadUrl(String sessionId, String storageName, String filename);

    boolean supportsDirectDownload();

    String resolveLegacyFileObjectKey(Long userId, String path, String storageName);
}
