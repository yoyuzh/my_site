package com.yoyuzh.transfer;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "portal_offline_transfer_session",
        indexes = {
                @Index(name = "idx_offline_transfer_expires_at", columnList = "expires_at")
        }
)
public class OfflineTransferSession {

    @Id
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "pickup_code", nullable = false, unique = true, length = 6)
    private String pickupCode;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ready", nullable = false)
    private boolean ready;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private final List<OfflineTransferFile> files = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPickupCode() {
        return pickupCode;
    }

    public void setPickupCode(String pickupCode) {
        this.pickupCode = pickupCode;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(Long senderUserId) {
        this.senderUserId = senderUserId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public List<OfflineTransferFile> getFiles() {
        return files;
    }

    public void addFile(OfflineTransferFile file) {
        files.add(file);
        file.setSession(this);
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
