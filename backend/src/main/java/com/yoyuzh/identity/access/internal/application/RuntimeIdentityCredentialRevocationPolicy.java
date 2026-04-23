package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.boot.security.AuthTokenInvalidationService;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.identity.access.api.IdentityCredentialRevocationPolicy;
import com.yoyuzh.identity.access.api.IdentityRefreshTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeIdentityCredentialRevocationPolicy implements IdentityCredentialRevocationPolicy {

    private final AuthTokenInvalidationService authTokenInvalidationService;
    private final IdentityRefreshTokenManager identityRefreshTokenManager;
    private final AuthSessionPolicy authSessionPolicy;
    private final UserRepository userRepository;

    @Override
    public void revokeAll(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        authTokenInvalidationService.revokeAccessTokensForUser(user.getId());
        authSessionPolicy.rotateAllActiveSessions(user);
        identityRefreshTokenManager.revokeAll(user.getId());
    }
}
