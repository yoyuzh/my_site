package com.yoyuzh.ops.admin.internal.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AdminDailyActiveUserRepository extends JpaRepository<AdminDailyActiveUserEntity, Long> {

    @Modifying
    @Query(value = """
            insert into portal_admin_daily_active_user (metric_date, user_id, username)
            select :metricDate, :userId, :username
            where not exists (
                select 1
                from portal_admin_daily_active_user
                where metric_date = :metricDate and user_id = :userId
            )
            """, nativeQuery = true)
    int insertIfAbsent(@Param("metricDate") LocalDate metricDate,
                       @Param("userId") Long userId,
                       @Param("username") String username);

    List<AdminDailyActiveUserEntity> findAllByMetricDateBetweenOrderByMetricDateAscUsernameAsc(LocalDate startDate, LocalDate endDate);

    Optional<AdminDailyActiveUserEntity> findByMetricDateAndUserId(LocalDate metricDate, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entry from AdminDailyActiveUserEntity entry
            where entry.metricDate = :metricDate and entry.userId = :userId
            """)
    Optional<AdminDailyActiveUserEntity> findByMetricDateAndUserIdForUpdate(@Param("metricDate") LocalDate metricDate,
                                                                            @Param("userId") Long userId);

    @Modifying
    void deleteAllByMetricDateBefore(LocalDate cutoffDate);
}
