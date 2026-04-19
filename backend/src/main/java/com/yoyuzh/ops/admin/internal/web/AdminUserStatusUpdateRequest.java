package com.yoyuzh.ops.admin.internal.web;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusUpdateRequest(@NotNull Boolean banned) {
}
