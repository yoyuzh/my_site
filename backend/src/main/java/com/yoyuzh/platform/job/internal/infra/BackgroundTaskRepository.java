package com.yoyuzh.platform.job.internal.infra;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BackgroundTaskRepository extends JpaRepository<BackgroundTask, Long> {

    Page<BackgroundTask> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
            select task from BackgroundTask task
            where (:userQuery is null or :userQuery = '' or exists (
                    select 1 from User owner
                    where owner.id = task.userId
                      and (
                          lower(owner.username) like lower(concat('%', :userQuery, '%'))
                          or lower(owner.email) like lower(concat('%', :userQuery, '%'))
                      )
            ))
              and (:type is null or task.type = :type)
              and (:status is null or task.status = :status)
              and (:failureCategoryPattern is null or lower(task.publicStateJson) like lower(concat('%', :failureCategoryPattern, '%')))
              and (:leaseState is null
                or (:leaseState = 'ACTIVE' and task.leaseOwner is not null and task.leaseExpiresAt is not null and task.leaseExpiresAt > :now)
                or (:leaseState = 'EXPIRED' and task.leaseOwner is not null and task.leaseExpiresAt is not null and task.leaseExpiresAt <= :now)
                or (:leaseState = 'NONE' and (task.leaseOwner is null or task.leaseExpiresAt is null)))
            """)
    Page<BackgroundTask> searchAdminTasks(@Param("userQuery") String userQuery,
                                          @Param("type") BackgroundTaskType type,
                                          @Param("status") BackgroundTaskStatus status,
                                          @Param("failureCategoryPattern") String failureCategoryPattern,
                                          @Param("leaseState") String leaseState,
                                          @Param("now") LocalDateTime now,
                                          Pageable pageable);

    Optional<BackgroundTask> findByIdAndUserId(Long id, Long userId);

    boolean existsByCorrelationId(String correlationId);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BackgroundTask task
            set task.status = :cancelledStatus,
                task.nextRunAt = null,
                task.leaseOwner = null,
                task.leaseExpiresAt = null,
                task.heartbeatAt = null,
                task.publicStateJson = :publicStateJson,
                task.finishedAt = :finishedAt,
                task.errorMessage = null,
                task.updatedAt = :updatedAt
            where task.id = :id
              and task.userId = :userId
              and task.updatedAt = :expectedUpdatedAt
              and task.status in :cancellableStatuses
            """)
    int cancelOwnedTask(@Param("id") Long id,
                        @Param("userId") Long userId,
                        @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt,
                        @Param("cancellableStatuses") Collection<BackgroundTaskStatus> cancellableStatuses,
                        @Param("cancelledStatus") BackgroundTaskStatus cancelledStatus,
                        @Param("publicStateJson") String publicStateJson,
                        @Param("finishedAt") LocalDateTime finishedAt,
                        @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BackgroundTask task
            set task.status = :queuedStatus,
                task.attemptCount = 0,
                task.nextRunAt = null,
                task.leaseOwner = null,
                task.leaseExpiresAt = null,
                task.heartbeatAt = null,
                task.publicStateJson = :publicStateJson,
                task.finishedAt = null,
                task.errorMessage = null,
                task.updatedAt = :updatedAt
            where task.id = :id
              and task.userId = :userId
              and task.updatedAt = :expectedUpdatedAt
              and task.status = :failedStatus
            """)
    int retryOwnedTask(@Param("id") Long id,
                       @Param("userId") Long userId,
                       @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt,
                       @Param("failedStatus") BackgroundTaskStatus failedStatus,
                       @Param("queuedStatus") BackgroundTaskStatus queuedStatus,
                       @Param("publicStateJson") String publicStateJson,
                       @Param("updatedAt") LocalDateTime updatedAt);

    @Query("""
            select count(task)
            from BackgroundTask task
            where task.status in :statuses
            """)
    long countByStatuses(@Param("statuses") Collection<BackgroundTaskStatus> statuses);
}
