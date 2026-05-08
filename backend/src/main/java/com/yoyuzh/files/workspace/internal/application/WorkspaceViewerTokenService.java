package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Component
public class WorkspaceViewerTokenService {

    private static final String PURPOSE = "workspace-viewer";

    private final String secret;
    private final long expirationSeconds;
    private final Clock clock;
    private SecretKey secretKey;

    @Autowired
    public WorkspaceViewerTokenService(@Value("${app.files.viewer-token-secret:}") String secret,
                                       @Value("${app.jwt.secret:}") String jwtSecret,
                                       @Value("${app.files.viewer-url-expiration-seconds}") long expirationSeconds) {
        this(resolveSecret(secret, jwtSecret), expirationSeconds, Clock.systemUTC());
    }

    WorkspaceViewerTokenService(String secret, long expirationSeconds, Clock clock) {
        this.secret = secret == null ? "" : secret.trim();
        this.expirationSeconds = Math.max(1L, expirationSeconds);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @PostConstruct
    void init() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("app.files.viewer-token-secret 未配置，且无法从 app.jwt.secret 继承");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("预览令牌密钥长度过短，至少需要 32 字节");
        }
        secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateViewerToken(Long userId, Long fileId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(PURPOSE)
                .claim("purpose", PURPOSE)
                .claim("uid", userId)
                .claim("fid", fileId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public ViewerTokenClaims parseViewerToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .clock(() -> Date.from(clock.instant()))
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!PURPOSE.equals(claims.get("purpose", String.class))) {
                throw invalidToken();
            }
            Long userId = parseLongClaim(claims, "uid");
            Long fileId = parseLongClaim(claims, "fid");
            return new ViewerTokenClaims(userId, fileId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidToken();
        }
    }

    private Long parseLongClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            throw invalidToken();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            throw invalidToken();
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.INVALID_INPUT, "预览链接无效或已过期");
    }

    private static String resolveSecret(String secret, String jwtSecret) {
        if (StringUtils.hasText(secret)) {
            return secret.trim();
        }
        return jwtSecret == null ? "" : jwtSecret.trim();
    }

    public record ViewerTokenClaims(Long userId, Long fileId) {
    }
}
