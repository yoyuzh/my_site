package com.yoyuzh.files.content.api;

import java.time.LocalDateTime;

public record ContentAdminFileBlobView(
        Long entityId,
        Long blobId,
        String objectKey,
        ContentEntityType entityType,
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
