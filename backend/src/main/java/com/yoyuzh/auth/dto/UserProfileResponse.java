package com.yoyuzh.auth.dto;

import com.yoyuzh.auth.UserRole;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String username,
        String displayName,
        String email,
        String phoneNumber,
        String bio,
        String preferredLanguage,
        String avatarUrl,
        UserRole role,
        LocalDateTime createdAt
) {
    public UserProfileResponse(Long id, String username, String email, LocalDateTime createdAt) {
        this(id, username, username, email, null, null, "zh-CN", null, UserRole.USER, createdAt);
    }
}
