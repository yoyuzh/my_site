package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.boot.security.AuthTokenInvalidationService;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.boot.security.JwtProperties;
import com.yoyuzh.identity.access.api.IdentityRefreshTokenManager;
import com.yoyuzh.identity.access.api.RotatedIdentityRefreshToken;
import com.yoyuzh.identity.access.internal.domain.RefreshToken;
import com.yoyuzh.identity.access.internal.infra.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RuntimeIdentityRefreshTokenManager implements IdentityRefreshTokenManager {

    private static final int REFRESH_TOKEN_BYTES = 48;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final AuthTokenInvalidationService authTokenInvalidationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String issue(Long userId, IdentityClientType clientType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setClientType(clientType.name());
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpirationSeconds()));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public RotatedIdentityRefreshToken rotate(String rawToken) {
        String tokenHash = hashToken(rawToken);
        if (authTokenInvalidationService.isRefreshTokenHashBlacklisted(tokenHash)) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "刷新令牌无效或已使用");
        }

        RefreshToken existing = refreshTokenRepository.findForUpdateByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "刷新令牌无效"));

        if (existing.isRevoked()) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "刷新令牌无效或已使用");
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            existing.revoke(LocalDateTime.now());
            authTokenInvalidationService.blacklistRefreshTokenHash(existing.getTokenHash(), toInstant(existing.getExpiresAt()));
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "刷新令牌已过期");
        }

        User user = existing.getUser();
        IdentityClientType clientType = IdentityClientType.fromHeader(existing.getClientType());
        existing.revoke(LocalDateTime.now());
        authTokenInvalidationService.blacklistRefreshTokenHash(existing.getTokenHash(), toInstant(existing.getExpiresAt()));
        revokeAll(user.getId(), clientType);

        String nextRefreshToken = issue(user.getId(), clientType);
        return new RotatedIdentityRefreshToken(user.getId(), nextRefreshToken, clientType);
    }

    @Override
    @Transactional
    public void revokeAll(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<RefreshToken> tokens = refreshTokenRepository.findActiveByUserId(userId, now);
        refreshTokenRepository.revokeAllActiveByUserId(userId, now);
        blacklistRefreshTokens(tokens);
    }

    @Override
    @Transactional
    public void revokeAll(Long userId, IdentityClientType clientType) {
        LocalDateTime now = LocalDateTime.now();
        List<RefreshToken> tokens = refreshTokenRepository.findActiveByUserIdAndClientType(userId, clientType.name(), now);
        refreshTokenRepository.revokeAllActiveByUserIdAndClientType(userId, clientType.name(), now);
        blacklistRefreshTokens(tokens);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "刷新令牌不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法初始化刷新令牌哈希算法", ex);
        }
    }

    private void blacklistRefreshTokens(List<RefreshToken> tokens) {
        for (RefreshToken token : tokens) {
            authTokenInvalidationService.blacklistRefreshTokenHash(token.getTokenHash(), toInstant(token.getExpiresAt()));
        }
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
