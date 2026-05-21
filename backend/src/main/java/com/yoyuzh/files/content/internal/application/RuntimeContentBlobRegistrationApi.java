package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.api.FileBlobStatus;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimeContentBlobRegistrationApi implements ContentBlobRegistrationApi {

    private final FileBlobRepository fileBlobRepository;

    @Override
    @Transactional
    public ContentBlobReference registerStoredBlob(String objectKey, String contentType, long size) {
        FileBlob saved = fileBlobRepository.save(createBlob(objectKey, contentType, size, FileBlobStatus.READY, null));
        return new ContentBlobReference(saved.getId(), saved.getObjectKey(), saved.getContentType(), saved.getSize());
    }

    @Override
    @Transactional
    public ContentBlobReference registerPendingBlob(String objectKey, String contentType, long size, String localTempPath) {
        FileBlob saved = fileBlobRepository.save(createBlob(objectKey, contentType, size, FileBlobStatus.PENDING, localTempPath));
        return new ContentBlobReference(saved.getId(), saved.getObjectKey(), saved.getContentType(), saved.getSize());
    }

    @Override
    @Transactional
    public void attachUploadTask(Long blobId, Long uploadTaskId) {
        FileBlob blob = requireBlob(blobId);
        blob.setUploadTaskId(uploadTaskId);
        fileBlobRepository.save(blob);
    }

    @Override
    @Transactional
    public void markBlobReady(Long blobId) {
        FileBlob blob = requireBlob(blobId);
        blob.setStatus(FileBlobStatus.READY);
        blob.setLocalTempPath(null);
        fileBlobRepository.save(blob);
    }

    @Override
    @Transactional
    public void markBlobFailed(Long blobId) {
        FileBlob blob = requireBlob(blobId);
        blob.setStatus(FileBlobStatus.FAILED);
        fileBlobRepository.save(blob);
    }

    private FileBlob createBlob(String objectKey,
                                String contentType,
                                long size,
                                FileBlobStatus status,
                                String localTempPath) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(size);
        blob.setStatus(status);
        blob.setLocalTempPath(localTempPath);
        return blob;
    }

    private FileBlob requireBlob(Long blobId) {
        return fileBlobRepository.findById(blobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件内容不存在"));
    }
}
