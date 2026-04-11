package com.yoyuzh.admin;

import com.yoyuzh.files.core.FileEntityType;

import java.time.LocalDateTime;

public record AdminFileBlobResponse(
        Long entityId,
        Long blobId,
        String objectKey,
        FileEntityType entityType,
        Long storagePolicyId,
        Long size,
        String contentType,
        Integer referenceCount,
        long linkedStoredFileCount,
        long linkedOwnerCount,
        String sampleOwnerUsername,
        String sampleOwnerEmail,
        Long createdByUserId,
        String createdByUsername,
        LocalDateTime createdAt,
        LocalDateTime blobCreatedAt,
        boolean blobMissing,
        boolean orphanRisk,
        boolean referenceMismatch
) {
}
