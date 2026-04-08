package com.yoyuzh.files;

import com.yoyuzh.auth.User;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_file_entity", indexes = {
        @Index(name = "uk_file_entity_key_type", columnList = "object_key,entity_type", unique = true),
        @Index(name = "idx_file_entity_created_at", columnList = "created_at")
})
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(nullable = false)
    private Long size;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    private FileEntityType entityType;

    @Column(name = "reference_count", nullable = false)
    private Integer referenceCount;

    @Column(name = "storage_policy_id")
    private Long storagePolicyId;

    @Column(name = "upload_session_id", length = 64)
    private String uploadSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(name = "props_json", columnDefinition = "TEXT")
    private String propsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (referenceCount == null) {
            referenceCount = 0;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public FileEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(FileEntityType entityType) {
        this.entityType = entityType;
    }

    public Integer getReferenceCount() {
        return referenceCount;
    }

    public void setReferenceCount(Integer referenceCount) {
        this.referenceCount = referenceCount;
    }

    public Long getStoragePolicyId() {
        return storagePolicyId;
    }

    public void setStoragePolicyId(Long storagePolicyId) {
        this.storagePolicyId = storagePolicyId;
    }

    public String getUploadSessionId() {
        return uploadSessionId;
    }

    public void setUploadSessionId(String uploadSessionId) {
        this.uploadSessionId = uploadSessionId;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public String getPropsJson() {
        return propsJson;
    }

    public void setPropsJson(String propsJson) {
        this.propsJson = propsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
