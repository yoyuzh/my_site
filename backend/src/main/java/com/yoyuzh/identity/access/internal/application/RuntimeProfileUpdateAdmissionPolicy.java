package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.identity.access.api.ProfileUpdateAdmissionPolicy;
import com.yoyuzh.identity.access.api.ProfileUpdateAttempt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProfileUpdateAdmissionPolicy implements ProfileUpdateAdmissionPolicy {

    private final UserRepository userRepository;

    @Override
    public void assertAllowed(ProfileUpdateAttempt attempt) {
        if (!attempt.currentEmail().equalsIgnoreCase(attempt.nextEmail())
                && userRepository.existsByEmail(attempt.nextEmail())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "邮箱已存在");
        }
        if (!attempt.currentPhoneNumber().equals(attempt.nextPhoneNumber())
                && userRepository.existsByPhoneNumber(attempt.nextPhoneNumber())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "手机号已存在");
        }
    }
}
