package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentityRefreshTokenManager;
import com.yoyuzh.identity.access.api.RotatedIdentityRefreshToken;
import com.yoyuzh.identity.access.internal.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final IdentityRefreshTokenManager identityRefreshTokenManager;

    public String issueRefreshToken(User user) {
        return issueRefreshToken(user, IdentityClientType.DESKTOP);
    }

    public String issueRefreshToken(User user, IdentityClientType clientType) {
        return identityRefreshTokenManager.issue(user, clientType);
    }

    public RotatedRefreshToken rotateRefreshToken(String rawToken) {
        RotatedIdentityRefreshToken rotated = identityRefreshTokenManager.rotate(rawToken);
        return new RotatedRefreshToken(rotated.user(), rotated.refreshToken(), rotated.clientType());
    }

    public void revokeAllForUser(Long userId) {
        identityRefreshTokenManager.revokeAll(userId);
    }

    public void revokeAllForUser(Long userId, IdentityClientType clientType) {
        identityRefreshTokenManager.revokeAll(userId, clientType);
    }

    public record RotatedRefreshToken(User user, String refreshToken, IdentityClientType clientType) {
    }
}
