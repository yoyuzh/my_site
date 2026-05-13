package com.yoyuzh.identity.access.api;

import java.time.LocalDateTime;

public record UserWebDavCredentialResponse(
        String username,
        String endpoint,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String plaintextPassword
) {
}
