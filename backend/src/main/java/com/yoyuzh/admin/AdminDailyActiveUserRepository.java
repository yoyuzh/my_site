package com.yoyuzh.admin;

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

    List<AdminDailyActiveUserEntity> findAllByMetricDateBetweenOrderByMetricDateAscUsernameAsc(LocalDate startDate, LocalDate endDate);

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
