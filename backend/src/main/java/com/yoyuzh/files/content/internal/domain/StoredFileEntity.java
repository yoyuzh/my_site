package com.yoyuzh.files.content.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_stored_file_entity", indexes = {
        @Index(name = "uk_stored_file_entity_role", columnList = "stored_file_id,file_entity_id,entity_role", unique = true),
        @Index(name = "idx_stored_file_entity_file", columnList = "stored_file_id"),
        @Index(name = "idx_stored_file_entity_entity", columnList = "file_entity_id")
})
public class StoredFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stored_file_id", nullable = false)
    private Long storedFileId;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @jakarta.persistence.JoinColumn(name = "file_entity_id", nullable = false)
    private FileEntity fileEntity;

    @Column(name = "entity_role", nullable = false, length = 32)
    private String entityRole;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
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

    public Long getStoredFileId() {
        return storedFileId;
    }

    public void setStoredFileId(Long storedFileId) {
        this.storedFileId = storedFileId;
    }

    public FileEntity getFileEntity() {
        return fileEntity;
    }

    public void setFileEntity(FileEntity fileEntity) {
        this.fileEntity = fileEntity;
    }

    public String getEntityRole() {
        return entityRole;
    }

    public void setEntityRole(String entityRole) {
        this.entityRole = entityRole;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
