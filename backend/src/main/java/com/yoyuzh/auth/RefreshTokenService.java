package com.yoyuzh.auth;

import com.yoyuzh.identity.access.api.IdentityRefreshTokenManager;
import com.yoyuzh.identity.access.api.RotatedIdentityRefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final IdentityRefreshTokenManager identityRefreshTokenManager;

    public String issueRefreshToken(User user) {
        return issueRefreshToken(user, AuthClientType.DESKTOP);
    }

    public String issueRefreshToken(User user, AuthClientType clientType) {
        return identityRefreshTokenManager.issue(user, clientType);
    }

    public RotatedRefreshToken rotateRefreshToken(String rawToken) {
        RotatedIdentityRefreshToken rotated = identityRefreshTokenManager.rotate(rawToken);
        return new RotatedRefreshToken(rotated.user(), rotated.refreshToken(), rotated.clientType());
    }

    public void revokeAllForUser(Long userId) {
        identityRefreshTokenManager.revokeAll(userId);
    }

    public void revokeAllForUser(Long userId, AuthClientType clientType) {
        identityRefreshTokenManager.revokeAll(userId, clientType);
    }

    public record RotatedRefreshToken(User user, String refreshToken, AuthClientType clientType) {
    }
}
