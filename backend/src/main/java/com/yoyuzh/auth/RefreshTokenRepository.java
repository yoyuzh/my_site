package com.yoyuzh.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findForUpdateByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken token
            set token.revoked = true, token.revokedAt = :revokedAt
            where token.user.id = :userId and token.revoked = false
            """)
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying
    @Query("""
            update RefreshToken token
            set token.revoked = true, token.revokedAt = :revokedAt
            where token.user.id = :userId and token.revoked = false
              and (token.clientType = :clientType or (:clientType = 'DESKTOP' and token.clientType is null))
            """)
    int revokeAllActiveByUserIdAndClientType(@Param("userId") Long userId,
                                             @Param("clientType") String clientType,
                                             @Param("revokedAt") LocalDateTime revokedAt);

    @Query("""
            select token from RefreshToken token
            where token.user.id = :userId and token.revoked = false and token.expiresAt > :now
            """)
    List<RefreshToken> findActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("""
            select token from RefreshToken token
            where token.user.id = :userId and token.revoked = false and token.expiresAt > :now
              and (token.clientType = :clientType or (:clientType = 'DESKTOP' and token.clientType is null))
            """)
    List<RefreshToken> findActiveByUserIdAndClientType(@Param("userId") Long userId,
                                                       @Param("clientType") String clientType,
                                                       @Param("now") LocalDateTime now);
}
