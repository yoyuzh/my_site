package com.yoyuzh.identity.access.api;

import com.yoyuzh.auth.AuthClientType;
import com.yoyuzh.auth.User;

public interface IdentityRefreshTokenManager {

    String issue(User user, AuthClientType clientType);

    RotatedIdentityRefreshToken rotate(String rawToken);

    void revokeAll(Long userId);

    void revokeAll(Long userId, AuthClientType clientType);
}
