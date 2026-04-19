package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.admin.AdminRuntimeSettingsService;
import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.identity.access.api.RegistrationAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeRegistrationAdmissionPolicyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RegistrationInviteService registrationInviteService;

    @Mock
    private AdminRuntimeSettingsService adminRuntimeSettingsService;

    @Test
    void shouldRejectDuplicateUsername() {
        RuntimeRegistrationAdmissionPolicy policy =
                new RuntimeRegistrationAdmissionPolicy(userRepository, registrationInviteService, adminRuntimeSettingsService);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> policy.assertAllowed(attempt()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    void shouldRejectDuplicatePhoneNumber() {
        RuntimeRegistrationAdmissionPolicy policy =
                new RuntimeRegistrationAdmissionPolicy(userRepository, registrationInviteService, adminRuntimeSettingsService);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("13800138000")).thenReturn(true);

        assertThatThrownBy(() -> policy.assertAllowed(attempt()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号已存在");
    }

    @Test
    void shouldConsumeInviteCodeWhenRequired() {
        RuntimeRegistrationAdmissionPolicy policy =
                new RuntimeRegistrationAdmissionPolicy(userRepository, registrationInviteService, adminRuntimeSettingsService);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("13800138000")).thenReturn(false);
        when(adminRuntimeSettingsService.isInviteCodeRequired()).thenReturn(true);

        assertThatCode(() -> policy.assertAllowed(attempt())).doesNotThrowAnyException();

        verify(registrationInviteService).consumeInviteCode("invite-code");
    }

    private static RegistrationAttempt attempt() {
        return new RegistrationAttempt("alice", "alice@example.com", "13800138000", "invite-code");
    }
}
