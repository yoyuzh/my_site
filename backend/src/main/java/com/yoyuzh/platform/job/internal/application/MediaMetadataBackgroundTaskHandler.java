package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.search.FileMetadata;
import com.yoyuzh.files.search.FileMetadataRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import jakarta.transaction.Transactional;
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

    private final StoredFileRepository storedFileRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final FileContentStorage fileContentStorage;
    private final BackgroundTaskStateManager stateManager;

    public MediaMetadataBackgroundTaskHandler(StoredFileRepository storedFileRepository,
                                              FileMetadataRepository fileMetadataRepository,
                                              FileContentStorage fileContentStorage,
                                              BackgroundTaskStateManager stateManager) {
        this.storedFileRepository = storedFileRepository;
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
        StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, task.getUserId())
                .orElseThrow(() -> new IllegalStateException("media metadata task file not found"));
        if (file.isDirectory()) {
            throw new IllegalStateException("media metadata task only supports files");
        }
        FileBlob blob = Optional.ofNullable(file.getBlob())
                .orElseThrow(() -> new IllegalStateException("media metadata task requires blob"));
        if (!StringUtils.hasText(blob.getObjectKey())) {
            throw new IllegalStateException("media metadata task requires blob");
        }

        String contentType = firstText(file.getContentType(), blob.getContentType());
        long size = firstLong(file.getSize(), blob.getSize());
        progressReporter.report(Map.of("metadataStage", "loading-content"));
        byte[] content = Optional.ofNullable(fileContentStorage.readBlob(blob.getObjectKey()))
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

    private void upsertMetadata(StoredFile file, String name, String value) {
        FileMetadata metadata = fileMetadataRepository.findByFileIdAndName(file.getId(), name)
                .orElseGet(FileMetadata::new);
        metadata.setFile(file);
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
