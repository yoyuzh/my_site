package com.yoyuzh.identity.access.api;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.internal.domain.User;

public record RotatedIdentityRefreshToken(User user, String refreshToken, IdentityClientType clientType) {
}
