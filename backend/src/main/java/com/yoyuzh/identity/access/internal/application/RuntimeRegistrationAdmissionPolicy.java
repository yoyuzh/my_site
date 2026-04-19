package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.admin.AdminRuntimeSettingsService;
import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.identity.access.api.RegistrationAdmissionPolicy;
import com.yoyuzh.identity.access.api.RegistrationAttempt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeRegistrationAdmissionPolicy implements RegistrationAdmissionPolicy {

    private final UserRepository userRepository;
    private final RegistrationInviteService registrationInviteService;
    private final AdminRuntimeSettingsService adminRuntimeSettingsService;

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
        if (adminRuntimeSettingsService.isInviteCodeRequired()) {
            registrationInviteService.consumeInviteCode(attempt.inviteCode());
        }
    }
}
