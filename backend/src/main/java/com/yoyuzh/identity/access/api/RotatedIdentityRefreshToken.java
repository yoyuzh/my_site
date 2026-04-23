package com.yoyuzh.identity.access.api;

public record RotatedIdentityRefreshToken(Long userId, String refreshToken, IdentityClientType clientType) {
}
