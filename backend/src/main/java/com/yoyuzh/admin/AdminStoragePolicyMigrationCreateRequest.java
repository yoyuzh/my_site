package com.yoyuzh.admin;

import jakarta.validation.constraints.NotNull;

public record AdminStoragePolicyMigrationCreateRequest(
        @NotNull(message = "sourcePolicyId 不能为空")
        Long sourcePolicyId,
        @NotNull(message = "targetPolicyId 不能为空")
        Long targetPolicyId,
        String correlationId
) {
}
