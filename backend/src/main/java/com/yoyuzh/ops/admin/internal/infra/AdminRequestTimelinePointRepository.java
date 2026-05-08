package com.yoyuzh.ops.admin.internal.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AdminRequestTimelinePointRepository extends JpaRepository<AdminRequestTimelinePointEntity, Long> {

    List<AdminRequestTimelinePointEntity> findAllByMetricDateOrderByHourAsc(LocalDate metricDate);

    @Modifying
    @Query("""
            update AdminRequestTimelinePointEntity point
            set point.requestCount = point.requestCount + :delta,
                point.updatedAt = :updatedAt
            where point.metricDate = :metricDate and point.hour = :hour
            """)
    int incrementRequestCount(@Param("metricDate") LocalDate metricDate,
                              @Param("hour") int hour,
                              @Param("delta") long delta,
                              @Param("updatedAt") LocalDateTime updatedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select point from AdminRequestTimelinePointEntity point
            where point.metricDate = :metricDate and point.hour = :hour
            """)
    Optional<AdminRequestTimelinePointEntity> findByMetricDateAndHourForUpdate(@Param("metricDate") LocalDate metricDate,
                                                                               @Param("hour") int hour);
}
