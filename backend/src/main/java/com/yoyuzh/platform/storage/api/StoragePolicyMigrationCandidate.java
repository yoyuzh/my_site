package com.yoyuzh.platform.storage.api;

public record StoragePolicyMigrationCandidate(
        Long sourcePolicyId,
        String sourcePolicyName,
        Long targetPolicyId,
        String targetPolicyName,
        long candidateEntityCount,
        long candidateStoredFileCount,
        String entityType
) {
}
