package com.yoyuzh.identity.access.api;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.internal.domain.User;

public interface IdentityRefreshTokenManager {

    String issue(User user, IdentityClientType clientType);

    RotatedIdentityRefreshToken rotate(String rawToken);

    void revokeAll(Long userId);

    void revokeAll(Long userId, IdentityClientType clientType);
}
