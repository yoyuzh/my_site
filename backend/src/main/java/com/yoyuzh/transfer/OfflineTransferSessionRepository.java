package com.yoyuzh.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OfflineTransferSessionRepository extends JpaRepository<OfflineTransferSession, String> {

    boolean existsByPickupCode(String pickupCode);

    @Query("""
            select distinct session
            from OfflineTransferSession session
            left join fetch session.files
            where session.sessionId = :sessionId
            """)
    Optional<OfflineTransferSession> findWithFilesBySessionId(@Param("sessionId") String sessionId);

    @Query("""
            select distinct session
            from OfflineTransferSession session
            left join fetch session.files
            where session.pickupCode = :pickupCode
            """)
    Optional<OfflineTransferSession> findWithFilesByPickupCode(@Param("pickupCode") String pickupCode);

    @Query("""
            select distinct session
            from OfflineTransferSession session
            left join fetch session.files
            where session.expiresAt < :now
            """)
    List<OfflineTransferSession> findAllExpiredWithFiles(@Param("now") Instant now);
}
