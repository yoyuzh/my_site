package com.yoyuzh.auth.dto;

public record AuthResponse(String token, UserProfileResponse user) {
}
