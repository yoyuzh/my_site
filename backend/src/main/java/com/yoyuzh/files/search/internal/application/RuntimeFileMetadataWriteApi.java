package com.yoyuzh.files.search.internal.application;

import com.yoyuzh.files.search.api.FileMetadataWriteApi;
import com.yoyuzh.files.search.internal.domain.FileMetadata;
import com.yoyuzh.files.search.internal.infra.FileMetadataRepository;
import org.springframework.stereotype.Service;

@Service
public class RuntimeFileMetadataWriteApi implements FileMetadataWriteApi {

    private final FileMetadataRepository fileMetadataRepository;

    public RuntimeFileMetadataWriteApi(FileMetadataRepository fileMetadataRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
    }

    @Override
    public void upsertPublicMetadata(Long fileId, String name, String value) {
        FileMetadata metadata = fileMetadataRepository.findByFileIdAndName(fileId, name)
                .orElseGet(FileMetadata::new);
        metadata.setFileId(fileId);
        metadata.setName(name);
        metadata.setValue(value == null ? "" : value);
        metadata.setPublicVisible(true);
        fileMetadataRepository.save(metadata);
    }
}
