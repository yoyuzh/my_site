package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.workspace.api.WorkspaceFileQueryApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.files.search.FileMetadata;
import com.yoyuzh.files.search.FileMetadataRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional
public class MediaMetadataBackgroundTaskHandler implements BackgroundTaskHandler {

    private static final String MEDIA_CONTENT_TYPE = "media:contentType";
    private static final String MEDIA_SIZE = "media:size";
    private static final String MEDIA_WIDTH = "media:width";
    private static final String MEDIA_HEIGHT = "media:height";

    private final WorkspaceFileQueryApi workspaceFileQueryApi;
    private final ContentBlobQueryApi contentBlobQueryApi;
    private final FileMetadataRepository fileMetadataRepository;
    private final FileContentStorage fileContentStorage;
    private final BackgroundTaskStateManager stateManager;

    public MediaMetadataBackgroundTaskHandler(WorkspaceFileQueryApi workspaceFileQueryApi,
                                              FileMetadataRepository fileMetadataRepository,
                                              FileContentStorage fileContentStorage,
                                              BackgroundTaskStateManager stateManager) {
        this(
                workspaceFileQueryApi,
                blobId -> Optional.empty(),
                fileMetadataRepository,
                fileContentStorage,
                stateManager
        );
    }

    @Autowired
    public MediaMetadataBackgroundTaskHandler(WorkspaceFileQueryApi workspaceFileQueryApi,
                                              ContentBlobQueryApi contentBlobQueryApi,
                                              FileMetadataRepository fileMetadataRepository,
                                              FileContentStorage fileContentStorage,
                                              BackgroundTaskStateManager stateManager) {
        this.workspaceFileQueryApi = workspaceFileQueryApi;
        this.contentBlobQueryApi = contentBlobQueryApi;
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileContentStorage = fileContentStorage;
        this.stateManager = stateManager;
    }

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.MEDIA_META;
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task) {
        return handle(task, publicStatePatch -> {
        });
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        Long fileId = readFileId(task);
        WorkspaceFileSnapshot file = workspaceFileQueryApi.findOwnedActiveFile(task.getUserId(), fileId)
                .orElseThrow(() -> new IllegalStateException("media metadata task file not found"));
        if (file.directory()) {
            throw new IllegalStateException("media metadata task only supports files");
        }
        ContentBlobReference blob = Optional.ofNullable(file.blobId())
                .flatMap(contentBlobQueryApi::findBlobReferenceById)
                .orElseThrow(() -> new IllegalStateException("media metadata task requires blob"));
        if (!StringUtils.hasText(blob.objectKey())) {
            throw new IllegalStateException("media metadata task requires blob");
        }

        String contentType = firstText(file.contentType(), blob.contentType());
        long size = firstLong(file.size(), blob.size());
        progressReporter.report(Map.of("metadataStage", "loading-content"));
        byte[] content = Optional.ofNullable(fileContentStorage.readBlob(blob.objectKey()))
                .orElseThrow(() -> new IllegalStateException("media metadata task requires blob content"));

        Map<String, Object> publicStatePatch = new LinkedHashMap<>();
        publicStatePatch.put("worker", "media-metadata");
        publicStatePatch.put("metadataExtracted", true);
        publicStatePatch.put("mediaContentType", contentType);
        publicStatePatch.put("mediaSize", size);

        upsertMetadata(file, MEDIA_CONTENT_TYPE, contentType);
        upsertMetadata(file, MEDIA_SIZE, String.valueOf(size));

        try {
            progressReporter.report(Map.of("metadataStage", "reading-image"));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image != null) {
                upsertMetadata(file, MEDIA_WIDTH, String.valueOf(image.getWidth()));
                upsertMetadata(file, MEDIA_HEIGHT, String.valueOf(image.getHeight()));
                publicStatePatch.put("mediaWidth", image.getWidth());
                publicStatePatch.put("mediaHeight", image.getHeight());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("media metadata task failed to read image dimensions", ex);
        }

        publicStatePatch.put("metadataStage", "completed");
        return new BackgroundTaskHandlerResult(publicStatePatch);
    }

    private void upsertMetadata(WorkspaceFileSnapshot file, String name, String value) {
        FileMetadata metadata = fileMetadataRepository.findByFileIdAndName(file.id(), name)
                .orElseGet(FileMetadata::new);
        metadata.setFileId(file.id());
        metadata.setName(name);
        metadata.setValue(value == null ? "" : value);
        metadata.setPublicVisible(true);
        fileMetadataRepository.save(metadata);
    }

    private Long readFileId(BackgroundTask task) {
        Long fileId = stateManager.readLong(
                stateManager.parseJsonObject(task.getPrivateStateJson(), "media metadata task state is invalid").get("fileId")
        );
        if (fileId != null) {
            return fileId;
        }
        fileId = stateManager.readLong(
                stateManager.parseJsonObject(task.getPublicStateJson(), "media metadata task state is invalid").get("fileId")
        );
        if (fileId != null) {
            return fileId;
        }
        throw new IllegalStateException("media metadata task missing fileId");
    }

    private String firstText(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return "";
    }

    private long firstLong(Long primary, Long fallback) {
        if (primary != null) {
            return primary;
        }
        if (fallback != null) {
            return fallback;
        }
        return 0L;
    }
}
