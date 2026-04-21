package com.yoyuzh.ops.admin.internal.application;

public record AdminStoragePolicyMigrationInput(
        Long sourcePolicyId,
        Long targetPolicyId,
        String correlationId
) {
}
