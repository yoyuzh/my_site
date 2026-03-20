package com.yoyuzh.auth;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int REFRESH_TOKEN_BYTES = 48;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String issueRefreshToken(User user) {
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpirationSeconds()));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public RotatedRefreshToken rotateRefreshToken(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findForUpdateByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "刷新令牌无效"));

        if (existing.isRevoked()) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "刷新令牌无效或已使用");
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            existing.revoke(LocalDateTime.now());
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "刷新令牌已过期");
        }

        User user = existing.getUser();
        existing.revoke(LocalDateTime.now());
        revokeAllForUser(user.getId());

        String nextRefreshToken = issueRefreshToken(user);
        return new RotatedRefreshToken(user, nextRefreshToken);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, LocalDateTime.now());
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

    public record RotatedRefreshToken(User user, String refreshToken) {
    }
}
