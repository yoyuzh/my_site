package com.yoyuzh.identity.access.api;

import com.yoyuzh.auth.AuthClientType;
import com.yoyuzh.auth.User;

public record RotatedIdentityRefreshToken(User user, String refreshToken, AuthClientType clientType) {
}
