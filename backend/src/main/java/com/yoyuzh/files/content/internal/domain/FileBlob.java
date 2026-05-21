package com.yoyuzh.files.content.internal.domain;

import com.yoyuzh.files.content.api.FileBlobStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_file_blob", indexes = {
        @Index(name = "uk_file_blob_object_key", columnList = "object_key", unique = true),
        @Index(name = "idx_file_blob_created_at", columnList = "created_at")
})
public class FileBlob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, length = 512, unique = true)
    private String objectKey;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileBlobStatus status;

    @Column(name = "local_temp_path", length = 512)
    private String localTempPath;

    @Column(name = "upload_task_id")
    private Long uploadTaskId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = FileBlobStatus.READY;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
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

    public FileBlobStatus getStatus() {
        return status;
    }

    public void setStatus(FileBlobStatus status) {
        this.status = status;
    }

    public String getLocalTempPath() {
        return localTempPath;
    }

    public void setLocalTempPath(String localTempPath) {
        this.localTempPath = localTempPath;
    }

    public Long getUploadTaskId() {
        return uploadTaskId;
    }

    public void setUploadTaskId(Long uploadTaskId) {
        this.uploadTaskId = uploadTaskId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
