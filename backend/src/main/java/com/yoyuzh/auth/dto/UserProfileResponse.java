package com.yoyuzh.auth.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(Long id, String username, String email, LocalDateTime createdAt) {
}
