package com.yoyuzh.identity.access.api;

import com.yoyuzh.identity.access.api.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserPasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = 64, message = PasswordPolicy.VALIDATION_MESSAGE)
        String newPassword
) {

    @AssertTrue(message = PasswordPolicy.VALIDATION_MESSAGE)
    public boolean isPasswordStrong() {
        return PasswordPolicy.isStrong(newPassword);
    }
}
