package com.yoyuzh.files.upload;

import com.yoyuzh.identity.access.internal.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_upload_session", indexes = {
        @Index(name = "uk_upload_session_session_id", columnList = "session_id", unique = true),
        @Index(name = "idx_upload_session_user_status", columnList = "user_id,status"),
        @Index(name = "idx_upload_session_expires_at", columnList = "expires_at")
})
public class UploadSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "target_path", nullable = false, length = 512)
    private String targetPath;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "multipart_upload_id", length = 255)
    private String multipartUploadId;

    @Column(name = "storage_policy_id")
    private Long storagePolicyId;

    @Column(name = "chunk_size", nullable = false)
    private Long chunkSize;

    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount;

    @Column(name = "uploaded_parts_json", columnDefinition = "TEXT")
    private String uploadedPartsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UploadSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = UploadSessionStatus.CREATED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getMultipartUploadId() {
        return multipartUploadId;
    }

    public void setMultipartUploadId(String multipartUploadId) {
        this.multipartUploadId = multipartUploadId;
    }

    public Long getStoragePolicyId() {
        return storagePolicyId;
    }

    public void setStoragePolicyId(Long storagePolicyId) {
        this.storagePolicyId = storagePolicyId;
    }

    public Long getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Long chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getUploadedPartsJson() {
        return uploadedPartsJson;
    }

    public void setUploadedPartsJson(String uploadedPartsJson) {
        this.uploadedPartsJson = uploadedPartsJson;
    }

    public UploadSessionStatus getStatus() {
        return status;
    }

    public void setStatus(UploadSessionStatus status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
