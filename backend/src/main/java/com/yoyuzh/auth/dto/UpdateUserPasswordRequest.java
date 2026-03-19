package com.yoyuzh.auth.dto;

import com.yoyuzh.auth.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserPasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = 10, max = 64, message = "密码至少10位，且必须包含大写字母、小写字母、数字和特殊字符")
        String newPassword
) {

    @AssertTrue(message = "密码至少10位，且必须包含大写字母、小写字母、数字和特殊字符")
    public boolean isPasswordStrong() {
        return PasswordPolicy.isStrong(newPassword);
    }
}
