package com.yoyuzh.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "portal_offline_transfer_file",
        indexes = {
                @Index(name = "idx_offline_transfer_file_session", columnList = "session_id")
        }
)
public class OfflineTransferFile {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private OfflineTransferSession session;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "relative_path", nullable = false, length = 512)
    private String relativePath;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "storage_name", nullable = false, length = 255)
    private String storageName;

    @Column(name = "uploaded", nullable = false)
    private boolean uploaded;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public OfflineTransferSession getSession() {
        return session;
    }

    public void setSession(OfflineTransferSession session) {
        this.session = session;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getStorageName() {
        return storageName;
    }

    public void setStorageName(String storageName) {
        this.storageName = storageName;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }
}
