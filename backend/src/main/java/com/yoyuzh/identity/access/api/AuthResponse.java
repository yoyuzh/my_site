package com.yoyuzh.identity.access.api;

public record AuthResponse(String token, String accessToken, String refreshToken, UserProfileResponse user) {

    public static AuthResponse issued(String accessToken, String refreshToken, UserProfileResponse user) {
        return new AuthResponse(accessToken, accessToken, refreshToken, user);
    }
}
