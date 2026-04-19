package com.yoyuzh.ops.admin.api;

import java.time.LocalDateTime;

public record AdminFileBlobResponse(
        Long entityId,
        Long blobId,
        String objectKey,
        AdminFileEntityType entityType,
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
