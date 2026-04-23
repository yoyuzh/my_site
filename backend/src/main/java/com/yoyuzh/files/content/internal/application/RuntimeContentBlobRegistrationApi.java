package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeContentBlobRegistrationApi implements ContentBlobRegistrationApi {

    private final FileBlobRepository fileBlobRepository;

    @Override
    public ContentBlobReference registerStoredBlob(String objectKey, String contentType, long size) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(size);
        FileBlob saved = fileBlobRepository.save(blob);
        return new ContentBlobReference(saved.getId(), saved.getObjectKey(), saved.getContentType(), saved.getSize());
    }
}
