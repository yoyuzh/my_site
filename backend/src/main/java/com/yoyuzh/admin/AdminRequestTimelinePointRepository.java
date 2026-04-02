package com.yoyuzh.admin;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AdminRequestTimelinePointRepository extends JpaRepository<AdminRequestTimelinePointEntity, Long> {

    List<AdminRequestTimelinePointEntity> findAllByMetricDateOrderByHourAsc(LocalDate metricDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select point from AdminRequestTimelinePointEntity point
            where point.metricDate = :metricDate and point.hour = :hour
            """)
    Optional<AdminRequestTimelinePointEntity> findByMetricDateAndHourForUpdate(@Param("metricDate") LocalDate metricDate,
                                                                               @Param("hour") int hour);
}
