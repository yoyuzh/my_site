package com.yoyuzh.admin;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusUpdateRequest(@NotNull Boolean banned) {
}
