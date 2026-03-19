package com.yoyuzh.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
}
