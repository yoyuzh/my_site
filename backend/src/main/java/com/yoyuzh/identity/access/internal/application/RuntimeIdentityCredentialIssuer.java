package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
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
    public IssuedAuthCredentials issueFresh(Long userId, IdentityClientType clientType) {
        User user = findUser(userId);
        authTokenInvalidationService.revokeAccessTokensForUser(user.getId(), clientType);
        identityRefreshTokenManager.revokeAll(user.getId(), clientType);
        return issueWithRefreshToken(user.getId(), identityRefreshTokenManager.issue(user.getId(), clientType), clientType);
    }

    @Override
    @Transactional
    public IssuedAuthCredentials issueWithRefreshToken(Long userId, String refreshToken, IdentityClientType clientType) {
        User user = findUser(userId);
        authSessionPolicy.rotateActiveSession(user, clientType);
        User sessionUser = userRepository.save(user);
        String accessToken = jwtTokenProvider.generateAccessToken(
                sessionUser.getId(),
                sessionUser.getUsername(),
                authSessionPolicy.getActiveSessionId(sessionUser, clientType),
                clientType);
        return new IssuedAuthCredentials(toSnapshot(sessionUser), accessToken, refreshToken);
    }

    @Override
    @Transactional
    public IssuedAuthCredentials refresh(String rawRefreshToken, IdentityClientType defaultClientType) {
        var rotated = identityRefreshTokenManager.rotate(rawRefreshToken);
        IdentityClientType clientType = rotated.clientType() == null ? defaultClientType : rotated.clientType();
        return issueWithRefreshToken(rotated.userId(), rotated.refreshToken(), clientType);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
    }

    private IdentityUserSnapshot toSnapshot(User user) {
        return new IdentityUserSnapshot(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getBio(),
                user.getPreferredLanguage(),
                user.getAvatarStorageName(),
                user.getAvatarContentType(),
                user.getAvatarUpdatedAt(),
                user.getRole() == null ? IdentityRoleName.USER : IdentityRoleName.valueOf(user.getRole().name()),
                user.getCreatedAt(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }
}
