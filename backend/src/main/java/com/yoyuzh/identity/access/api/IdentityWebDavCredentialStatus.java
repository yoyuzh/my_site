package com.yoyuzh.identity.access.api;

import java.time.LocalDateTime;

public record IdentityWebDavCredentialStatus(
        Long userId,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static IdentityWebDavCredentialStatus missing(Long userId) {
        return new IdentityWebDavCredentialStatus(userId, false, null, null);
    }
}
