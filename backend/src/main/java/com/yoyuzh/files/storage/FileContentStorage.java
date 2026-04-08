package com.yoyuzh.files.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileContentStorage {

    PreparedUpload prepareUpload(Long userId, String path, String storageName, String contentType, long size);

    void upload(Long userId, String path, String storageName, MultipartFile file);

    void completeUpload(Long userId, String path, String storageName, String contentType, long size);

    byte[] readFile(Long userId, String path, String storageName);

    void deleteFile(Long userId, String path, String storageName);

    String createDownloadUrl(Long userId, String path, String storageName, String filename);

    default void renameFile(Long userId, String path, String oldStorageName, String newStorageName) {
        throw new UnsupportedOperationException("File content rename is not supported by this storage");
    }

    default void renameDirectory(Long userId, String oldPath, String oldStorageName, String newStorageName) {
        throw new UnsupportedOperationException("Directory content rename is not supported by this storage");
    }

    default void moveFile(Long userId, String oldPath, String storageName, String newPath) {
        throw new UnsupportedOperationException("File content move is not supported by this storage");
    }

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

    byte[] readBlob(String objectKey);

    void deleteBlob(String objectKey);

    String createBlobDownloadUrl(String objectKey, String filename);

    void createDirectory(Long userId, String logicalPath);

    void ensureDirectory(Long userId, String logicalPath);

    void storeTransferFile(String sessionId, String storageName, String contentType, byte[] content);

    byte[] readTransferFile(String sessionId, String storageName);

    void deleteTransferFile(String sessionId, String storageName);

    String createTransferDownloadUrl(String sessionId, String storageName, String filename);

    boolean supportsDirectDownload();

    String resolveLegacyFileObjectKey(Long userId, String path, String storageName);
}
