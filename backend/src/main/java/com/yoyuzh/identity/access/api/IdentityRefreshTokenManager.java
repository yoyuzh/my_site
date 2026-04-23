package com.yoyuzh.identity.access.api;

public interface IdentityRefreshTokenManager {

    String issue(Long userId, IdentityClientType clientType);

    RotatedIdentityRefreshToken rotate(String rawToken);

    void revokeAll(Long userId);

    void revokeAll(Long userId, IdentityClientType clientType);
}
