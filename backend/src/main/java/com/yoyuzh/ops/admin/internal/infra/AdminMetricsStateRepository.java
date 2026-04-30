package com.yoyuzh.ops.admin.internal.infra;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminMetricsStateRepository extends JpaRepository<AdminMetricsState, Long> {

    @Modifying
    @Query(value = """
            insert into portal_admin_metrics_state (
                id,
                request_count,
                request_count_date,
                download_traffic_bytes,
                transfer_usage_bytes,
                offline_transfer_storage_limit_bytes,
                updated_at
            )
            select
                :id,
                :requestCount,
                :requestCountDate,
                :downloadTrafficBytes,
                :transferUsageBytes,
                :offlineTransferStorageLimitBytes,
                :updatedAt
            where not exists (
                select 1 from portal_admin_metrics_state where id = :id
            )
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") Long id,
                       @Param("requestCount") long requestCount,
                       @Param("requestCountDate") LocalDate requestCountDate,
                       @Param("downloadTrafficBytes") long downloadTrafficBytes,
                       @Param("transferUsageBytes") long transferUsageBytes,
                       @Param("offlineTransferStorageLimitBytes") long offlineTransferStorageLimitBytes,
                       @Param("updatedAt") LocalDateTime updatedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from AdminMetricsState state where state.id = :id")
    Optional<AdminMetricsState> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
            update AdminMetricsState state
            set state.requestCount = case when state.requestCountDate = :metricDate then state.requestCount + 1 else 1 end,
                state.requestCountDate = :metricDate,
                state.updatedAt = :updatedAt
            where state.id = :id
            """)
    int incrementRequestCount(@Param("id") Long id,
                              @Param("metricDate") LocalDate metricDate,
                              @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("""
            update AdminMetricsState state
            set state.downloadTrafficBytes = state.downloadTrafficBytes + :bytes,
                state.updatedAt = :updatedAt
            where state.id = :id
            """)
    int incrementDownloadTrafficBytes(@Param("id") Long id,
                                      @Param("bytes") long bytes,
                                      @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("""
            update AdminMetricsState state
            set state.transferUsageBytes = state.transferUsageBytes + :bytes,
                state.updatedAt = :updatedAt
            where state.id = :id
            """)
    int incrementTransferUsageBytes(@Param("id") Long id,
                                    @Param("bytes") long bytes,
                                    @Param("updatedAt") LocalDateTime updatedAt);
}
