package com.yoyuzh.files.content.api;

public record ContentStoragePolicyMigrationItem(
        Long entityId,
        String objectKey,
        Long size,
        String contentType,
        Long blobId,
        String blobContentType,
        Long blobSize,
        long linkedStoredFileCount,
        String entityType
) {
}
