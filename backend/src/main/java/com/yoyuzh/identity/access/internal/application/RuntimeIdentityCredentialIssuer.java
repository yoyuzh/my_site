package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.auth.AuthClientType;
import com.yoyuzh.auth.AuthSessionPolicy;
import com.yoyuzh.auth.AuthTokenInvalidationService;
import com.yoyuzh.auth.JwtTokenProvider;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
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
    public IssuedAuthCredentials issueFresh(User user, AuthClientType clientType) {
        authTokenInvalidationService.revokeAccessTokensForUser(user.getId(), clientType);
        identityRefreshTokenManager.revokeAll(user.getId(), clientType);
        return issueWithRefreshToken(user, identityRefreshTokenManager.issue(user, clientType), clientType);
    }

    @Override
    @Transactional
    public IssuedAuthCredentials issueWithRefreshToken(User user, String refreshToken, AuthClientType clientType) {
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
    public IssuedAuthCredentials refresh(String rawRefreshToken, AuthClientType defaultClientType) {
        var rotated = identityRefreshTokenManager.rotate(rawRefreshToken);
        AuthClientType clientType = rotated.clientType() == null ? defaultClientType : rotated.clientType();
        return issueWithRefreshToken(rotated.user(), rotated.refreshToken(), clientType);
    }
}
