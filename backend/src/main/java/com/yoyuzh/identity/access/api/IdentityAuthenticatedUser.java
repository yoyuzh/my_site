package com.yoyuzh.identity.access.api;

public record IdentityAuthenticatedUser(
        Long id,
        String username,
        String passwordHash,
        IdentityRoleName role,
        boolean banned,
        String activeSessionId,
        String desktopActiveSessionId,
        String mobileActiveSessionId,
        long storageQuotaBytes,
        long maxUploadSizeBytes
) {
}
