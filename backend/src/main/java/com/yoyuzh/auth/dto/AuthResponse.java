package com.yoyuzh.auth.dto;

public record AuthResponse(String token, String accessToken, String refreshToken, UserProfileResponse user) {

    public static AuthResponse issued(String accessToken, String refreshToken, UserProfileResponse user) {
        return new AuthResponse(accessToken, accessToken, refreshToken, user);
    }
}
