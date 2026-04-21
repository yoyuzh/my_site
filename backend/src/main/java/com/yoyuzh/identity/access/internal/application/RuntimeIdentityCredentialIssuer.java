package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.internal.application.AuthSessionPolicy;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.boot.security.AuthTokenInvalidationService;
import com.yoyuzh.boot.security.JwtTokenProvider;
import com.yoyuzh.identity.access.api.IdentityCredentialIssuer;
import com.yoyuzh.identity.access.api.IdentityRefreshTokenManager;
import com.yoyuzh.identity.access.api.IssuedAuthCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimeIdentityCredentialIssuer implements IdentityCredentialIssuer {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final IdentityRefreshTokenManager identityRefreshTokenManager;
    private final AuthTokenInvalidationService authTokenInvalidationService;
    private final AuthSessionPolicy authSessionPolicy;

    @Override
    @Transactional
    public IssuedAuthCredentials issueFresh(User user, IdentityClientType clientType) {
        authTokenInvalidationService.revokeAccessTokensForUser(user.getId(), clientType);
        identityRefreshTokenManager.revokeAll(user.getId(), clientType);
        return issueWithRefreshToken(user, identityRefreshTokenManager.issue(user, clientType), clientType);
    }

    @Override
    @Transactional
    public IssuedAuthCredentials issueWithRefreshToken(User user, String refreshToken, IdentityClientType clientType) {
        authSessionPolicy.rotateActiveSession(user, clientType);
        User sessionUser = userRepository.save(user);
        String accessToken = jwtTokenProvider.generateAccessToken(
                sessionUser.getId(),
                sessionUser.getUsername(),
                authSessionPolicy.getActiveSessionId(sessionUser, clientType),
                clientType);
        return new IssuedAuthCredentials(sessionUser, accessToken, refreshToken);
    }

    @Override
    @Transactional
    public IssuedAuthCredentials refresh(String rawRefreshToken, IdentityClientType defaultClientType) {
        var rotated = identityRefreshTokenManager.rotate(rawRefreshToken);
        IdentityClientType clientType = rotated.clientType() == null ? defaultClientType : rotated.clientType();
        return issueWithRefreshToken(rotated.user(), rotated.refreshToken(), clientType);
    }
}
