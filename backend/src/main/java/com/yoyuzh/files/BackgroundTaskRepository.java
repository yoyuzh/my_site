package com.yoyuzh.files;

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

    @Modifying
    @Query("""
            update BackgroundTask task
            set task.status = :runningStatus,
                task.errorMessage = null,
                task.updatedAt = :updatedAt
            where task.id = :id
              and task.status = :queuedStatus
            """)
    int claimQueuedTask(@Param("id") Long id,
                        @Param("queuedStatus") BackgroundTaskStatus queuedStatus,
                        @Param("runningStatus") BackgroundTaskStatus runningStatus,
                        @Param("updatedAt") LocalDateTime updatedAt);
}
