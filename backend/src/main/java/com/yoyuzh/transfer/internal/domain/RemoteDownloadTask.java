package com.yoyuzh.transfer.internal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "portal_remote_download_task",
        indexes = {
                @Index(name = "idx_remote_download_user_created", columnList = "user_id,created_at"),
                @Index(name = "idx_remote_download_background_task", columnList = "background_task_id")
        }
)
public class RemoteDownloadTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_path", nullable = false, length = 1024)
    private String targetPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private RemoteDownloadSourceType sourceType;

    @Column(name = "source_value", nullable = false, length = 4096)
    private String sourceValue;

    @Lob
    @Column(name = "source_content")
    private byte[] sourceContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine_type", nullable = false, length = 32)
    private DownloadEngineType engineType;

    @Column(name = "download_node_id", nullable = false, length = 128)
    private String downloadNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RemoteDownloadStatus status;

    @Column(name = "background_task_id")
    private Long backgroundTaskId;

    @Column(name = "downloader_task_id", length = 255)
    private String downloaderTaskId;

    @Column(name = "selected_file_count", nullable = false)
    private int selectedFileCount;

    @Column(name = "imported_file_count", nullable = false)
    private int importedFileCount;

    @Column(name = "failure_code", length = 128)
    private String failureCode;

    @Column(name = "failure_message", length = 1024)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private final List<RemoteDownloadCandidateFile> candidateFiles = new ArrayList<>();

    public static RemoteDownloadTask createHttp(Long userId, String targetPath, String sourceValue, String downloadNodeId) {
        return create(userId, targetPath, RemoteDownloadSourceType.HTTP, sourceValue, null, DownloadEngineType.ARIA2, downloadNodeId);
    }

    public static RemoteDownloadTask createMagnet(Long userId, String targetPath, String sourceValue, String downloadNodeId) {
        return create(userId, targetPath, RemoteDownloadSourceType.MAGNET, sourceValue, null, DownloadEngineType.QBITTORRENT, downloadNodeId);
    }

    public static RemoteDownloadTask createTorrent(Long userId,
                                                   String targetPath,
                                                   String sourceValue,
                                                   byte[] sourceContent,
                                                   String downloadNodeId) {
        return create(
                userId,
                targetPath,
                RemoteDownloadSourceType.TORRENT_FILE,
                sourceValue,
                sourceContent,
                DownloadEngineType.QBITTORRENT,
                downloadNodeId
        );
    }

    private static RemoteDownloadTask create(Long userId,
                                             String targetPath,
                                             RemoteDownloadSourceType sourceType,
                                             String sourceValue,
                                             byte[] sourceContent,
                                             DownloadEngineType engineType,
                                             String downloadNodeId) {
        Instant now = Instant.now();
        RemoteDownloadTask task = new RemoteDownloadTask();
        task.userId = userId;
        task.targetPath = targetPath;
        task.sourceType = sourceType;
        task.sourceValue = sourceValue;
        task.sourceContent = sourceContent == null ? null : sourceContent.clone();
        task.engineType = engineType;
        task.downloadNodeId = downloadNodeId;
        task.status = RemoteDownloadStatus.PENDING;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public Long getId() {
        return id;
    }

    void setIdForTest(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public RemoteDownloadSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceValue() {
        return sourceValue;
    }

    public byte[] getSourceContent() {
        return sourceContent == null ? null : sourceContent.clone();
    }

    public DownloadEngineType getEngineType() {
        return engineType;
    }

    public String getDownloadNodeId() {
        return downloadNodeId;
    }

    public RemoteDownloadStatus getStatus() {
        return status;
    }

    public void setStatus(RemoteDownloadStatus status) {
        this.status = status;
    }

    public Long getBackgroundTaskId() {
        return backgroundTaskId;
    }

    public void setBackgroundTaskId(Long backgroundTaskId) {
        this.backgroundTaskId = backgroundTaskId;
    }

    public String getDownloaderTaskId() {
        return downloaderTaskId;
    }

    public void setDownloaderTaskId(String downloaderTaskId) {
        this.downloaderTaskId = downloaderTaskId;
    }

    public int getSelectedFileCount() {
        return selectedFileCount;
    }

    public void setSelectedFileCount(int selectedFileCount) {
        this.selectedFileCount = selectedFileCount;
    }

    public int getImportedFileCount() {
        return importedFileCount;
    }

    public void setImportedFileCount(int importedFileCount) {
        this.importedFileCount = importedFileCount;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public List<RemoteDownloadCandidateFile> getCandidateFiles() {
        return candidateFiles;
    }

    public void addCandidateFile(RemoteDownloadCandidateFile candidateFile) {
        candidateFiles.add(candidateFile);
        candidateFile.setTask(this);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
