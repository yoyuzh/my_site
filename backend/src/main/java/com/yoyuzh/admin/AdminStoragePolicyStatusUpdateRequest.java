package com.yoyuzh.admin;

import jakarta.validation.constraints.NotNull;

public record AdminStoragePolicyStatusUpdateRequest(
        @NotNull(message = "enabled 不能为空")
        Boolean enabled
) {
}
