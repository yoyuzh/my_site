package com.yoyuzh.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank @Size(min = 2, max = 64) String displayName,
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank
        @Pattern(regexp = "^1\\d{10}$", message = "请输入有效的11位手机号")
        String phoneNumber,
        @Size(max = 280) String bio,
        @Size(min = 2, max = 16) String preferredLanguage
) {
}
