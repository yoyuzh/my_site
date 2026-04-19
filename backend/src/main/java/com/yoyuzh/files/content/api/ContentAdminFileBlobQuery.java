package com.yoyuzh.files.content.api;

public record ContentAdminFileBlobQuery(
        int page,
        int size,
        String userQuery,
        Long storagePolicyId,
        String objectKey,
        ContentEntityType entityType
) {
}
