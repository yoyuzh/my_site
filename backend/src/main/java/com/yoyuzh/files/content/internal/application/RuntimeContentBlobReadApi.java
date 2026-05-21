package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobReadApi;
import com.yoyuzh.files.content.api.ContentBlobReadResult;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobStateView;
import com.yoyuzh.files.content.api.FileBlobStatus;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class RuntimeContentBlobReadApi implements ContentBlobReadApi {

    private final ContentBlobQueryApi contentBlobQueryApi;
    private final FileContentStorage fileContentStorage;

    public RuntimeContentBlobReadApi(ContentBlobQueryApi contentBlobQueryApi,
                                     FileContentStorage fileContentStorage) {
        this.contentBlobQueryApi = contentBlobQueryApi;
        this.fileContentStorage = fileContentStorage;
    }

    @Override
    public ContentBlobReadResult readBlob(Long blobId, boolean directory) {
        if (directory || blobId == null) {
            throw unavailable("文件内容不存在");
        }
        ContentBlobStateView state = contentBlobQueryApi.findBlobStateById(blobId)
                .orElseThrow(() -> unavailable("文件内容不存在"));
        return readState(state);
    }

    @Override
    public ContentBlobReadResult readBlob(ContentBlobReference blobReference) {
        if (blobReference == null || blobReference.blobId() == null) {
            throw unavailable("文件内容不存在");
        }
        ContentBlobStateView state = contentBlobQueryApi.findBlobStateById(blobReference.blobId())
                .orElseThrow(() -> unavailable("文件内容不存在"));
        return readState(state);
    }

    @Override
    public boolean isBlobReady(Long blobId, boolean directory) {
        if (directory || blobId == null) {
            return false;
        }
        return contentBlobQueryApi.findBlobStateById(blobId)
                .map(state -> resolveStatus(state) == FileBlobStatus.READY)
                .orElse(false);
    }

    private ContentBlobReadResult readState(ContentBlobStateView state) {
        FileBlobStatus status = resolveStatus(state);
        if (status == FileBlobStatus.READY) {
            InputStream content = fileContentStorage.readBlobStream(state.objectKey());
            if (content == null) {
                content = new java.io.ByteArrayInputStream(fileContentStorage.readBlob(state.objectKey()));
            }
            return new ContentBlobReadResult(
                    new ContentBlobReference(state.blobId(), state.objectKey(), state.contentType(), state.size()),
                    content,
                    state.size(),
                    false
            );
        }
        if (status == FileBlobStatus.PENDING) {
            Path tempPath = resolveTempPath(state.localTempPath());
            if (tempPath != null && Files.exists(tempPath)) {
                try {
                    return new ContentBlobReadResult(
                            new ContentBlobReference(state.blobId(), state.objectKey(), state.contentType(), state.size()),
                            Files.newInputStream(tempPath),
                            Files.size(tempPath),
                            true
                    );
                } catch (IOException ex) {
                    throw unavailable("文件内容暂不可用");
                }
            }
        }
        throw unavailable("文件内容暂不可用");
    }

    private FileBlobStatus resolveStatus(ContentBlobStateView state) {
        if (state == null || state.status() == null) {
            return FileBlobStatus.READY;
        }
        return state.status();
    }

    private Path resolveTempPath(String localTempPath) {
        if (localTempPath == null || localTempPath.isBlank()) {
            return null;
        }
        return Path.of(localTempPath).toAbsolutePath().normalize();
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
