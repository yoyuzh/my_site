package com.yoyuzh.identity.access.api;

import com.yoyuzh.auth.AuthClientType;

public record PasswordChangeAttempt(
        String currentPassword,
        String newPassword,
        AuthClientType clientType
) {
}
