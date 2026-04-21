package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.internal.application.AuthSessionPolicy;
import com.yoyuzh.identity.access.internal.domain.User;
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

    @Override
    @Transactional
    public IssuedAuthCredentials changePassword(User user, PasswordChangeAttempt attempt) {
        if (!passwordEncoder.matches(attempt.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "当前密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(attempt.newPassword()));
        identityCredentialRevocationPolicy.revokeAll(user);
        return identityCredentialIssuer.issueFresh(user, attempt.clientType());
    }
}
