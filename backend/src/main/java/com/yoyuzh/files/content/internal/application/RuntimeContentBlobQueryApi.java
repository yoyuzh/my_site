package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobStateView;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RuntimeContentBlobQueryApi implements ContentBlobQueryApi {

    private final FileBlobRepository fileBlobRepository;

    public RuntimeContentBlobQueryApi(FileBlobRepository fileBlobRepository) {
        this.fileBlobRepository = fileBlobRepository;
    }

    @Override
    public Optional<ContentBlobReference> findBlobReferenceById(Long blobId) {
        if (blobId == null) {
            return Optional.empty();
        }
        return findBlobStateById(blobId)
                .map(blob -> new ContentBlobReference(blob.blobId(), blob.objectKey(), blob.contentType(), blob.size()));
    }

    @Override
    public Optional<ContentBlobStateView> findBlobStateById(Long blobId) {
        if (blobId == null) {
            return Optional.empty();
        }
        return fileBlobRepository.findById(blobId)
                .map(blob -> new ContentBlobStateView(
                        blob.getId(),
                        blob.getObjectKey(),
                        blob.getContentType(),
                        blob.getSize(),
                        blob.getStatus(),
                        blob.getLocalTempPath(),
                        blob.getUploadTaskId()
                ));
    }
}
