package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.identity.access.api.IdentityCredentialIssuer;
import com.yoyuzh.identity.access.api.IdentityCredentialRevocationPolicy;
import com.yoyuzh.identity.access.api.IssuedAuthCredentials;
import com.yoyuzh.identity.access.api.PasswordChangeAttempt;
import com.yoyuzh.identity.access.api.PasswordChangePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimePasswordChangePolicy implements PasswordChangePolicy {

    private final PasswordEncoder passwordEncoder;
    private final IdentityCredentialRevocationPolicy identityCredentialRevocationPolicy;
    private final IdentityCredentialIssuer identityCredentialIssuer;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public IssuedAuthCredentials changePassword(Long userId, PasswordChangeAttempt attempt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        if (!passwordEncoder.matches(attempt.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "当前密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(attempt.newPassword()));
        userRepository.save(user);
        identityCredentialRevocationPolicy.revokeAll(user.getId());
        return identityCredentialIssuer.issueFresh(user.getId(), attempt.clientType());
    }
}
