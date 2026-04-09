package com.yoyuzh.files.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "portal_stored_file_entity", indexes = {
        @Index(name = "uk_stored_file_entity_role", columnList = "stored_file_id,file_entity_id,entity_role", unique = true),
        @Index(name = "idx_stored_file_entity_file", columnList = "stored_file_id"),
        @Index(name = "idx_stored_file_entity_entity", columnList = "file_entity_id")
})
public class StoredFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_file_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StoredFile storedFile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_entity_id", nullable = false)
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

    public StoredFile getStoredFile() {
        return storedFile;
    }

    public void setStoredFile(StoredFile storedFile) {
        this.storedFile = storedFile;
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
