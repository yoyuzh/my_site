package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.ops.admin.api.AdminRuntimeSettingsApi;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.identity.access.api.RegistrationAdmissionPolicy;
import com.yoyuzh.identity.access.api.RegistrationAttempt;
import com.yoyuzh.identity.access.internal.application.RegistrationInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeRegistrationAdmissionPolicy implements RegistrationAdmissionPolicy {

    private final UserRepository userRepository;
    private final RegistrationInviteService registrationInviteService;
    private final AdminRuntimeSettingsApi adminRuntimeSettingsApi;

    @Override
    public void assertAllowed(RegistrationAttempt attempt) {
        if (userRepository.existsByUsername(attempt.username())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "用户名已存在");
        }
        if (userRepository.existsByEmail(attempt.email())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "邮箱已存在");
        }
        if (userRepository.existsByPhoneNumber(attempt.phoneNumber())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "手机号已存在");
        }
        if (adminRuntimeSettingsApi.isInviteCodeRequired()) {
            registrationInviteService.consumeInviteCode(attempt.inviteCode());
        }
    }
}
