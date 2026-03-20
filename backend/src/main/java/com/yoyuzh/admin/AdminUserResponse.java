package com.yoyuzh.admin;

import com.yoyuzh.auth.UserRole;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String phoneNumber,
        LocalDateTime createdAt,
        String lastSchoolStudentId,
        String lastSchoolSemester,
        UserRole role,
        boolean banned
) {
}
