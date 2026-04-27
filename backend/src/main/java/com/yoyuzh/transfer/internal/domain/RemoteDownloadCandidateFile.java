package com.yoyuzh.transfer.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "portal_remote_download_candidate_file",
        indexes = {
                @Index(name = "idx_remote_download_candidate_task", columnList = "task_id")
        }
)
public class RemoteDownloadCandidateFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private RemoteDownloadTask task;

    @Column(name = "file_key", nullable = false, length = 128)
    private String fileKey;

    @Column(name = "relative_path", nullable = false, length = 1024)
    private String relativePath;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "selected", nullable = false)
    private boolean selected;

    public Long getId() {
        return id;
    }

    public RemoteDownloadTask getTask() {
        return task;
    }

    public void setTask(RemoteDownloadTask task) {
        this.task = task;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
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

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
