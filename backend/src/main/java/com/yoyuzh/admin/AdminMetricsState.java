package com.yoyuzh.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "portal_admin_metrics_state")
public class AdminMetricsState {

    @Id
    private Long id;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "request_count_date")
    private LocalDate requestCountDate;

    @Column(name = "download_traffic_bytes", nullable = false)
    private long downloadTrafficBytes;

    @Column(name = "transfer_usage_bytes", nullable = false)
    private long transferUsageBytes;

    @Column(name = "offline_transfer_storage_limit_bytes", nullable = false)
    private long offlineTransferStorageLimitBytes;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }

    public LocalDate getRequestCountDate() {
        return requestCountDate;
    }

    public void setRequestCountDate(LocalDate requestCountDate) {
        this.requestCountDate = requestCountDate;
    }

    public long getDownloadTrafficBytes() {
        return downloadTrafficBytes;
    }

    public void setDownloadTrafficBytes(long downloadTrafficBytes) {
        this.downloadTrafficBytes = downloadTrafficBytes;
    }

    public long getTransferUsageBytes() {
        return transferUsageBytes;
    }

    public void setTransferUsageBytes(long transferUsageBytes) {
        this.transferUsageBytes = transferUsageBytes;
    }

    public long getOfflineTransferStorageLimitBytes() {
        return offlineTransferStorageLimitBytes;
    }

    public void setOfflineTransferStorageLimitBytes(long offlineTransferStorageLimitBytes) {
        this.offlineTransferStorageLimitBytes = offlineTransferStorageLimitBytes;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
