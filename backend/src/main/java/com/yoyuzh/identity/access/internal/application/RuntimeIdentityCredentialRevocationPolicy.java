package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.internal.application.AuthSessionPolicy;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.boot.security.AuthTokenInvalidationService;
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

    @Override
    public void revokeAll(User user) {
        authTokenInvalidationService.revokeAccessTokensForUser(user.getId());
        authSessionPolicy.rotateAllActiveSessions(user);
        identityRefreshTokenManager.revokeAll(user.getId());
    }
}
