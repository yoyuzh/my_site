package com.yoyuzh.files.content.api;

public record ContentPrimaryEntity(
        Long entityId,
        String objectKey,
        String contentType,
        long size,
        int referenceCount,
        Long storagePolicyId
) {
}
