package com.yoyuzh.auth.dto;

import com.yoyuzh.auth.PasswordPolicy;
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
        @NotBlank @Size(min = 10, max = 64, message = "密码至少10位，且必须包含大写字母、小写字母、数字和特殊字符") String password,
        @NotBlank String confirmPassword,
        @NotBlank(message = "请输入邀请码") String inviteCode
) {

    @AssertTrue(message = "密码至少10位，且必须包含大写字母、小写字母、数字和特殊字符")
    public boolean isPasswordStrong() {
        return PasswordPolicy.isStrong(password);
    }

    @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
