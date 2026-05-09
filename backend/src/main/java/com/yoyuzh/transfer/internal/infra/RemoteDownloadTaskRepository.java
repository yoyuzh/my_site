package com.yoyuzh.transfer.internal.infra;

import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RemoteDownloadTaskRepository extends JpaRepository<RemoteDownloadTask, Long> {

    List<RemoteDownloadTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<RemoteDownloadTask> findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Long userId, Instant createdAt);

    @Query("""
            select task from RemoteDownloadTask task
            where task.userId = :userId
              and (task.status in :activeStatuses or task.createdAt >= :createdAt)
            order by task.createdAt desc
            """)
    List<RemoteDownloadTask> findActiveOrRecentByUserId(@Param("userId") Long userId,
                                                        @Param("activeStatuses") Collection<RemoteDownloadStatus> activeStatuses,
                                                        @Param("createdAt") Instant createdAt);

    Optional<RemoteDownloadTask> findByIdAndUserId(Long id, Long userId);
}
