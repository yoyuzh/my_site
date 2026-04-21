package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminStoragePolicyUpsertRequest(
        @NotBlank(message = "存储策略名称不能为空")
        String name,
        @NotNull(message = "存储策略类型不能为空")
        StoragePolicyType type,
        String bucketName,
        String endpoint,
        String region,
        boolean privateBucket,
        String prefix,
        @NotNull(message = "凭证模式不能为空")
        StoragePolicyCredentialMode credentialMode,
        @Positive(message = "最大对象大小必须大于 0")
        long maxSizeBytes,
        @NotNull(message = "能力声明不能为空")
        StoragePolicyCapabilities capabilities,
        boolean enabled
) {
}
