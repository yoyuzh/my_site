package com.yoyuzh.boot.security;

import com.yoyuzh.identity.access.api.IdentityClientType;

import java.time.Instant;

public record ParsedToken(
        String username,
        Long userId,
        String sessionId,
        IdentityClientType clientType,
        Instant issuedAt
) {
}
