package com.yoyuzh.auth;

import com.yoyuzh.auth.dto.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectWeakPassword() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "13800138000",
                "weakpass",
                "weakpass",
                "invite-code"
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getMessage())
                .contains("密码至少10位，且必须包含大写字母、小写字母、数字和特殊字符");
    }

    @Test
    void shouldAcceptStrongPassword() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "13800138000",
                "StrongPass1!",
                "StrongPass1!",
                "invite-code"
        );

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectInvalidPhoneNumber() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "12345",
                "StrongPass1!",
                "StrongPass1!",
                "invite-code"
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getMessage())
                .contains("请输入有效的11位手机号");
    }

    @Test
    void shouldRejectMismatchedPasswordConfirmation() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "13800138000",
                "StrongPass1!",
                "StrongPass2!",
                "invite-code"
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getMessage())
                .contains("两次输入的密码不一致");
    }

    @Test
    void shouldRejectBlankInviteCode() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "13800138000",
                "StrongPass1!",
                "StrongPass1!",
                ""
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getMessage())
                .contains("请输入邀请码");
    }
}
