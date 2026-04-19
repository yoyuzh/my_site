package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.identity.access.api.ProfileUpdateAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeProfileUpdateAdmissionPolicyTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldRejectDuplicateEmailForProfileUpdate() {
        RuntimeProfileUpdateAdmissionPolicy policy = new RuntimeProfileUpdateAdmissionPolicy(userRepository);
        when(userRepository.existsByEmail("newalice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> policy.assertAllowed(attempt("alice@example.com", "13800138000",
                        "newalice@example.com", "13800138000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱已存在");
    }

    @Test
    void shouldRejectDuplicatePhoneNumberForProfileUpdate() {
        RuntimeProfileUpdateAdmissionPolicy policy = new RuntimeProfileUpdateAdmissionPolicy(userRepository);
        when(userRepository.existsByPhoneNumber("13900139000")).thenReturn(true);

        assertThatThrownBy(() -> policy.assertAllowed(attempt("alice@example.com", "13800138000",
                        "alice@example.com", "13900139000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号已存在");
    }

    @Test
    void shouldAllowUnchangedIdentityFields() {
        RuntimeProfileUpdateAdmissionPolicy policy = new RuntimeProfileUpdateAdmissionPolicy(userRepository);

        assertThatCode(() -> policy.assertAllowed(attempt("alice@example.com", "13800138000",
                        "alice@example.com", "13800138000")))
                .doesNotThrowAnyException();
    }

    private static ProfileUpdateAttempt attempt(
            String currentEmail,
            String currentPhoneNumber,
            String nextEmail,
            String nextPhoneNumber) {
        return new ProfileUpdateAttempt(currentEmail, currentPhoneNumber, nextEmail, nextPhoneNumber);
    }
}
