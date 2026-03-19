package com.yoyuzh.admin;

import com.yoyuzh.auth.UserRole;
import jakarta.validation.constraints.NotNull;

public record AdminUserRoleUpdateRequest(@NotNull UserRole role) {
}
