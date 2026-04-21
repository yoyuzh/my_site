package com.yoyuzh.boot.security;

import com.yoyuzh.identity.access.api.IdentityClientType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAuthTokenInvalidationService extends AuthTokenInvalidationService {

    public NoOpAuthTokenInvalidationService() {
        super(null, null, null);
    }

    @Override
    public void revokeAccessTokensForUser(Long userId) {
    }

    @Override
    public void revokeAccessTokensForUser(Long userId, IdentityClientType clientType) {
    }

    @Override
    public boolean isAccessTokenRevoked(Long userId, IdentityClientType clientType, Instant issuedAt) {
        return false;
    }

    @Override
    public void blacklistRefreshTokenHash(String tokenHash, Instant expiresAt) {
    }

    @Override
    public boolean isRefreshTokenHashBlacklisted(String tokenHash) {
        return false;
    }
}
