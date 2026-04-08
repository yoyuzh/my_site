package com.yoyuzh.files;

import com.yoyuzh.auth.User;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_file", indexes = {
        @Index(name = "uk_file_user_path_name", columnList = "user_id,path,filename", unique = true),
        @Index(name = "idx_file_created_at", columnList = "created_at"),
        @Index(name = "idx_file_deleted_at", columnList = "deleted_at"),
        @Index(name = "idx_file_recycle_group", columnList = "recycle_group_id")
})
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, length = 512)
    private String path;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blob_id")
    private FileBlob blob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_entity_id")
    private FileEntity primaryEntity;

    @Column(name = "storage_name", length = 255)
    private String legacyStorageName;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Column(name = "is_directory", nullable = false)
    private boolean directory;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "recycle_original_path", length = 512)
    private String recycleOriginalPath;

    @Column(name = "recycle_group_id", length = 64)
    private String recycleGroupId;

    @Column(name = "is_recycle_root", nullable = false)
    private boolean recycleRoot;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public FileBlob getBlob() {
        return blob;
    }

    public void setBlob(FileBlob blob) {
        this.blob = blob;
    }

    public FileEntity getPrimaryEntity() {
        return primaryEntity;
    }

    public void setPrimaryEntity(FileEntity primaryEntity) {
        this.primaryEntity = primaryEntity;
    }

    public String getLegacyStorageName() {
        return legacyStorageName;
    }

    public void setLegacyStorageName(String legacyStorageName) {
        this.legacyStorageName = legacyStorageName;
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

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getRecycleOriginalPath() {
        return recycleOriginalPath;
    }

    public void setRecycleOriginalPath(String recycleOriginalPath) {
        this.recycleOriginalPath = recycleOriginalPath;
    }

    public String getRecycleGroupId() {
        return recycleGroupId;
    }

    public void setRecycleGroupId(String recycleGroupId) {
        this.recycleGroupId = recycleGroupId;
    }

    public boolean isRecycleRoot() {
        return recycleRoot;
    }

    public void setRecycleRoot(boolean recycleRoot) {
        this.recycleRoot = recycleRoot;
    }
}
