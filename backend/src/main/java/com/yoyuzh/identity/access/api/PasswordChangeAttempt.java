package com.yoyuzh.identity.access.api;

import com.yoyuzh.identity.access.api.IdentityClientType;

public record PasswordChangeAttempt(
        String currentPassword,
        String newPassword,
        IdentityClientType clientType
) {
}
