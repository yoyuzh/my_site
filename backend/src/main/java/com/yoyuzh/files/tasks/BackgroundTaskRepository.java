package com.yoyuzh.files.tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BackgroundTaskRepository extends JpaRepository<BackgroundTask, Long> {

    Page<BackgroundTask> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<BackgroundTask> findByIdAndUserId(Long id, Long userId);

    List<BackgroundTask> findByStatusOrderByCreatedAtAsc(BackgroundTaskStatus status, Pageable pageable);

    List<BackgroundTask> findByStatusOrderByUpdatedAtAsc(BackgroundTaskStatus status);

    @Query("""
            select task.id from BackgroundTask task
            where task.status = :status
              and (task.nextRunAt is null or task.nextRunAt <= :now)
            order by coalesce(task.nextRunAt, task.createdAt) asc, task.createdAt asc
            """)
    List<Long> findReadyTaskIdsByStatusOrder(@Param("status") BackgroundTaskStatus status,
                                             @Param("now") LocalDateTime now,
                                             Pageable pageable);

    @Modifying
    @Query("""
            update BackgroundTask task
            set task.status = :runningStatus,
                task.errorMessage = null,
                task.nextRunAt = null,
                task.attemptCount = task.attemptCount + 1,
                task.leaseOwner = :leaseOwner,
                task.leaseExpiresAt = :leaseExpiresAt,
                task.heartbeatAt = :heartbeatAt,
                task.updatedAt = :updatedAt
            where task.id = :id
              and task.status = :queuedStatus
            """)
    int claimQueuedTask(@Param("id") Long id,
                        @Param("queuedStatus") BackgroundTaskStatus queuedStatus,
                        @Param("runningStatus") BackgroundTaskStatus runningStatus,
                        @Param("leaseOwner") String leaseOwner,
                        @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
                        @Param("heartbeatAt") LocalDateTime heartbeatAt,
                        @Param("updatedAt") LocalDateTime updatedAt);

    @Query("""
            select task.id from BackgroundTask task
            where task.status = :status
              and (task.leaseExpiresAt is null or task.leaseExpiresAt <= :now)
            order by coalesce(task.leaseExpiresAt, task.updatedAt, task.createdAt) asc
            """)
    List<Long> findExpiredRunningTaskIds(@Param("status") BackgroundTaskStatus status,
                                         @Param("now") LocalDateTime now,
                                         Pageable pageable);

    @Modifying
    @Query("""
            update BackgroundTask task
            set task.status = :queuedStatus,
                task.errorMessage = null,
                task.finishedAt = null,
                task.nextRunAt = null,
                task.leaseOwner = null,
                task.leaseExpiresAt = null,
                task.heartbeatAt = null,
                task.updatedAt = :updatedAt
            where task.id = :id
              and task.status = :runningStatus
              and (task.leaseExpiresAt is null or task.leaseExpiresAt <= :now)
            """)
    int requeueExpiredRunningTask(@Param("id") Long id,
                                  @Param("runningStatus") BackgroundTaskStatus runningStatus,
                                  @Param("queuedStatus") BackgroundTaskStatus queuedStatus,
                                  @Param("now") LocalDateTime now,
                                  @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("""
            update BackgroundTask task
            set task.leaseExpiresAt = :leaseExpiresAt,
                task.heartbeatAt = :heartbeatAt,
                task.updatedAt = :updatedAt
            where task.id = :id
              and task.status = :runningStatus
              and task.leaseOwner = :leaseOwner
            """)
    int refreshRunningTaskLease(@Param("id") Long id,
                                @Param("runningStatus") BackgroundTaskStatus runningStatus,
                                @Param("leaseOwner") String leaseOwner,
                                @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
                                @Param("heartbeatAt") LocalDateTime heartbeatAt,
                                @Param("updatedAt") LocalDateTime updatedAt);
}
