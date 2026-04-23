package com.yoyuzh.transfer.internal.infra;

import com.yoyuzh.transfer.internal.domain.OfflineTransferSession;
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

    @Query("""
            select distinct session
            from OfflineTransferSession session
            left join fetch session.files
            where session.senderUserId = :senderUserId and session.expiresAt >= :now
            order by session.expiresAt desc
            """)
    List<OfflineTransferSession> findActiveWithFilesBySenderUserId(@Param("senderUserId") Long senderUserId,
                                                                   @Param("now") Instant now);

    @Query("""
            select coalesce(sum(file.size), 0)
            from OfflineTransferFile file
            join file.session session
            where file.uploaded = true and session.expiresAt >= :now
            """)
    long sumUploadedFileSizeByExpiresAtAfter(@Param("now") Instant now);
}
