package com.yoyuzh.identity.access.api;

import com.yoyuzh.identity.access.api.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank
        @Pattern(regexp = "^1\\d{10}$", message = "请输入有效的11位手机号")
        String phoneNumber,
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = 64, message = PasswordPolicy.VALIDATION_MESSAGE) String password,
        @NotBlank String confirmPassword,
        @NotBlank(message = "请输入邀请码") String inviteCode
) {

    @AssertTrue(message = PasswordPolicy.VALIDATION_MESSAGE)
    public boolean isPasswordStrong() {
        return PasswordPolicy.isStrong(password);
    }

    @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
