package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceContentBindingApi;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingFile;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RuntimeWorkspaceContentBindingApi implements WorkspaceContentBindingApi {

    private final StoredFileRepository storedFileRepository;

    public RuntimeWorkspaceContentBindingApi(StoredFileRepository storedFileRepository) {
        this.storedFileRepository = storedFileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceContentBindingFile> findFilesMissingBlobBindings() {
        return storedFileRepository.findAllByDirectoryFalseAndBlobIdIsNull().stream()
                .map(this::toBindingFile)
                .toList();
    }

    @Override
    @Transactional
    public void attachBlob(Long fileId, Long blobId) {
        storedFileRepository.findById(fileId).ifPresent(file -> {
            file.setBlobId(blobId);
            storedFileRepository.save(file);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceContentBindingFile> findFilesMissingPrimaryEntityBindings() {
        return storedFileRepository.findAllByDirectoryFalseAndBlobIdIsNotNullAndPrimaryEntityIdIsNull().stream()
                .map(this::toBindingFile)
                .toList();
    }

    @Override
    @Transactional
    public void attachPrimaryEntity(Long fileId, Long primaryEntityId) {
        storedFileRepository.findById(fileId).ifPresent(file -> {
            file.setPrimaryEntityId(primaryEntityId);
            storedFileRepository.save(file);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public long countFilesByBlobId(Long blobId) {
        return storedFileRepository.countByBlobId(blobId);
    }

    private WorkspaceContentBindingFile toBindingFile(StoredFile file) {
        return new WorkspaceContentBindingFile(
                file.getId(),
                file.getUserId(),
                file.getPath(),
                file.getLegacyStorageName(),
                file.getContentType(),
                file.getSize(),
                file.getBlobId()
        );
    }
}
