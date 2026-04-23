package com.yoyuzh.files.content.api;

public record ContentStoragePolicyMigrationInspection(
        long entityCount,
        long storedFileCount,
        String entityType
) {
}
