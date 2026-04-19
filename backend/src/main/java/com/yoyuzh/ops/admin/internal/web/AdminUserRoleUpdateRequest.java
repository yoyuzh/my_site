package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.ops.admin.api.AdminUserRole;
import jakarta.validation.constraints.NotNull;

public record AdminUserRoleUpdateRequest(@NotNull AdminUserRole role) {
}
