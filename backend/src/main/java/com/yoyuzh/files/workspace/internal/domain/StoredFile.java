package com.yoyuzh.files.workspace.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "portal_file", indexes = {
        @Index(name = "uk_file_user_path_name", columnList = "user_id,path,filename", unique = true),
        @Index(name = "idx_file_created_at", columnList = "created_at"),
        @Index(name = "idx_file_deleted_at", columnList = "deleted_at"),
        @Index(name = "idx_file_recycle_group", columnList = "recycle_group_id"),
        @Index(name = "idx_file_user_deleted_category", columnList = "user_id,deleted_at,search_category")
})
public class StoredFile {

    private static final Set<String> DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown"
    );

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "avif"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "mov", "m4v", "mkv", "avi", "webm"
    );
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "wav", "flac", "aac", "m4a", "ogg"
    );
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(name = "blob_id")
    private Long blobId;

    @Column(name = "primary_entity_id")
    private Long primaryEntityId;

    @Column(name = "storage_name", length = 255)
    private String legacyStorageName;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "search_category", length = 32)
    private String searchCategory;

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

    @Column(nullable = false)
    private boolean favorite;

    @Column(name = "custom_emoji", length = 32)
    private String customEmoji;

    @Column(name = "folder_color", length = 16)
    private String folderColor;

    public static StoredFile directory(Long userId, String parentPath, String directoryName) {
        StoredFile storedFile = new StoredFile();
        storedFile.setUserId(userId);
        storedFile.setFilename(directoryName);
        storedFile.setPath(parentPath);
        storedFile.setLegacyStorageName(directoryName);
        storedFile.setContentType("directory");
        storedFile.setSize(0L);
        storedFile.setDirectory(true);
        return storedFile;
    }

    public static StoredFile blobBackedFile(Long userId,
                                            String path,
                                            String filename,
                                            String contentType,
                                            long size,
                                            Long blobId,
                                            String legacyStorageName,
                                            Long primaryEntityId) {
        StoredFile storedFile = new StoredFile();
        storedFile.setUserId(userId);
        storedFile.setFilename(filename);
        storedFile.setPath(path);
        storedFile.setContentType(contentType);
        storedFile.setSize(size);
        storedFile.setDirectory(false);
        storedFile.setBlobId(blobId);
        storedFile.setLegacyStorageName(legacyStorageName);
        storedFile.setPrimaryEntityId(primaryEntityId);
        return storedFile;
    }

    @PrePersist
    public void prePersist() {
        refreshSearchCategory();
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        refreshSearchCategory();
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
        refreshSearchCategory();
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Long getBlobId() {
        return blobId;
    }

    public void setBlobId(Long blobId) {
        this.blobId = blobId;
    }

    public Long getPrimaryEntityId() {
        return primaryEntityId;
    }

    public void setPrimaryEntityId(Long primaryEntityId) {
        this.primaryEntityId = primaryEntityId;
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
        refreshSearchCategory();
    }

    public String getSearchCategory() {
        return searchCategory;
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
        refreshSearchCategory();
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

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public String getCustomEmoji() {
        return customEmoji;
    }

    public void setCustomEmoji(String customEmoji) {
        this.customEmoji = customEmoji;
    }

    public String getFolderColor() {
        return folderColor;
    }

    public void setFolderColor(String folderColor) {
        this.folderColor = folderColor;
    }

    public String logicalPath() {
        return "/".equals(path) ? "/" + filename : path + "/" + filename;
    }

    public void renameTo(String sanitizedFilename) {
        setFilename(sanitizedFilename);
    }

    public void moveTo(String normalizedTargetPath) {
        this.path = normalizedTargetPath;
    }

    public void relocateForAncestorMove(String oldLogicalPath, String newLogicalPath) {
        if (path.equals(oldLogicalPath)) {
            this.path = newLogicalPath;
            return;
        }
        this.path = newLogicalPath + path.substring(oldLogicalPath.length());
    }

    public void markFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void updateAppearance(String customEmoji, String folderColor) {
        this.customEmoji = customEmoji;
        this.folderColor = directory ? folderColor : null;
    }

    public void recycleTo(String recyclePath,
                          String originalPath,
                          String recycleGroupId,
                          boolean recycleRoot,
                          LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        this.recycleOriginalPath = originalPath;
        this.recycleGroupId = recycleGroupId;
        this.recycleRoot = recycleRoot;
        this.path = recyclePath;
    }

    public void restoreFromRecycleBin() {
        this.path = recycleOriginalPath;
        this.deletedAt = null;
        this.recycleOriginalPath = null;
        this.recycleGroupId = null;
        this.recycleRoot = false;
    }

    public StoredFile copyForOwner(Long ownerUserId, String nextPath) {
        StoredFile copiedFile = new StoredFile();
        copiedFile.setUserId(ownerUserId);
        copiedFile.setFilename(filename);
        copiedFile.setPath(nextPath);
        copiedFile.setContentType(contentType);
        copiedFile.setSize(size);
        copiedFile.setDirectory(directory);
        copiedFile.setBlobId(blobId);
        copiedFile.setCustomEmoji(customEmoji);
        copiedFile.setFolderColor(directory ? folderColor : null);
        return copiedFile;
    }

    private void refreshSearchCategory() {
        this.searchCategory = deriveSearchCategory();
    }

    private String deriveSearchCategory() {
        if (directory) {
            return null;
        }
        String normalizedContentType = normalizeContentType(contentType);
        if (normalizedContentType.startsWith("image/")) {
            return "image";
        }
        if (normalizedContentType.startsWith("video/")) {
            return "video";
        }
        if (normalizedContentType.startsWith("audio/")) {
            return "audio";
        }
        if (DOCUMENT_CONTENT_TYPES.contains(normalizedContentType)) {
            return "document";
        }

        String extension = extractExtension(filename);
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return "video";
        }
        if (AUDIO_EXTENSIONS.contains(extension)) {
            return "audio";
        }
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return "document";
        }
        return null;
    }

    private String normalizeContentType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String extractExtension(String value) {
        if (value == null) {
            return "";
        }
        int dotIndex = value.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == value.length() - 1) {
            return "";
        }
        return value.substring(dotIndex + 1).trim().toLowerCase(Locale.ROOT);
    }
}
