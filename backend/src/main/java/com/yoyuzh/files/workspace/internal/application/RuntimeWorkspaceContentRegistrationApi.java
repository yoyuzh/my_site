package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentPrimaryEntityApi;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.FileListDirectoryCacheService;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeWorkspaceContentRegistrationApi implements ContentRegistrationApi, ContentDuplicationApi {

    private final StoredFileRepository storedFileRepository;
    private final ContentPrimaryEntityApi contentPrimaryEntityApi;
    private final ContentBlobLifecycleApi contentBlobLifecycleApi;
    private final FileListDirectoryCacheService fileListDirectoryCacheService;
    private final WorkspacePathPolicy workspacePathPolicy;

    @Autowired
    public RuntimeWorkspaceContentRegistrationApi(StoredFileRepository storedFileRepository,
                                                  ContentPrimaryEntityApi contentPrimaryEntityApi,
                                                  ContentBlobLifecycleApi contentBlobLifecycleApi,
                                                  FileListDirectoryCacheService fileListDirectoryCacheService,
                                                  WorkspacePathPolicy workspacePathPolicy) {
        this.storedFileRepository = storedFileRepository;
        this.contentPrimaryEntityApi = contentPrimaryEntityApi;
        this.contentBlobLifecycleApi = contentBlobLifecycleApi;
        this.fileListDirectoryCacheService = fileListDirectoryCacheService == null
                ? FileListDirectoryCacheService.noOp()
                : fileListDirectoryCacheService;
        this.workspacePathPolicy = workspacePathPolicy;
    }

    @Override
    public RegisteredContentFile registerBlob(ContentRegistrationCommand command) {
        String resolvedFilename = workspacePathPolicy.resolveAvailableNodeName(
                command.userId(),
                command.normalizedPath(),
                command.filename()
        );
        return persistBlobBackedFile(new ContentRegistrationCommand(
                command.userId(),
                command.normalizedPath(),
                resolvedFilename,
                command.contentType(),
                command.size(),
                command.blob()
        ));
    }

    @Override
    public RegisteredContentFile duplicateBlobBackedFile(ContentRegistrationCommand command) {
        return persistBlobBackedFile(command);
    }

    private RegisteredContentFile persistBlobBackedFile(ContentRegistrationCommand command) {
        ContentPrimaryEntity primaryEntity = contentPrimaryEntityApi.createOrReferencePrimaryEntity(command.userId(), command.blob());
        StoredFile storedFile = StoredFile.blobBackedFile(
                command.userId(),
                command.normalizedPath(),
                command.filename(),
                command.contentType(),
                command.size(),
                command.blob().blobId(),
                command.blob().objectKey(),
                primaryEntity.entityId()
        );
        StoredFile savedFile = storedFileRepository.save(storedFile);
        contentPrimaryEntityApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(savedFile.getId(), primaryEntity.entityId()));
        fileListDirectoryCacheService.touchDirectory(command.userId(), command.normalizedPath());
        return toRegisteredContentFile(savedFile);
    }

    public void finalizeReplace(Long userId,
                                Long targetFileId,
                                String contentType,
                                long size,
                                Long newBlobId,
                                String newObjectKey,
                                Long newPrimaryEntityId) {
        storedFileRepository.findDetailedByIdAndUserId(targetFileId, userId)
                .ifPresent(file -> {
                    Long oldBlobId = file.getBlobId();
                    Long oldPrimaryEntityId = file.getPrimaryEntityId();
                    file.setBlobId(newBlobId);
                    file.setPrimaryEntityId(newPrimaryEntityId);
                    file.setLegacyStorageName(newObjectKey);
                    file.setContentType(contentType);
                    file.setSize(size);
                    storedFileRepository.save(file);
                    if (contentPrimaryEntityApi != null && oldPrimaryEntityId != null && !oldPrimaryEntityId.equals(newPrimaryEntityId)) {
                        contentPrimaryEntityApi.releasePrimaryEntity(file.getId(), oldPrimaryEntityId);
                    }
                    if (contentBlobLifecycleApi != null && oldBlobId != null && !oldBlobId.equals(newBlobId)) {
                        contentBlobLifecycleApi.deleteBlobReferences(
                                contentBlobLifecycleApi.collectBlobReferencesToDelete(java.util.List.of(oldBlobId))
                        );
                    }
                    fileListDirectoryCacheService.touchDirectory(userId, file.getPath());
                });
    }

    private RegisteredContentFile toRegisteredContentFile(StoredFile storedFile) {
        return new RegisteredContentFile(
                storedFile.getId(),
                storedFile.getFilename(),
                storedFile.getPath(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt()
        );
    }
}
