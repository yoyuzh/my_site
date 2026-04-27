package com.yoyuzh.files.content.api;

public record ContentStoragePolicyMigrationMutation(
        Long entityId,
        Long blobId,
        String nextObjectKey
) {
}
