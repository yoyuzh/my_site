package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeWorkspaceContentRegistrationApi implements ContentRegistrationApi, ContentDuplicationApi {

    private final StoredFileRepository storedFileRepository;
    private final ContentAssetApi contentAssetApi;

    public RuntimeWorkspaceContentRegistrationApi(StoredFileRepository storedFileRepository,
                                                  ContentAssetApi contentAssetApi) {
        this.storedFileRepository = storedFileRepository;
        this.contentAssetApi = contentAssetApi;
    }

    @Override
    public RegisteredContentFile registerBlob(ContentRegistrationCommand command) {
        return persistBlobBackedFile(command);
    }

    @Override
    public RegisteredContentFile duplicateBlobBackedFile(ContentRegistrationCommand command) {
        return persistBlobBackedFile(command);
    }

    private RegisteredContentFile persistBlobBackedFile(ContentRegistrationCommand command) {
        StoredFile storedFile = new StoredFile();
        storedFile.setUserId(command.userId());
        storedFile.setFilename(command.filename());
        storedFile.setPath(command.normalizedPath());
        storedFile.setContentType(command.contentType());
        storedFile.setSize(command.size());
        storedFile.setDirectory(false);
        storedFile.setBlobId(command.blob().blobId());
        storedFile.setLegacyStorageName(command.blob().objectKey());
        ContentPrimaryEntity primaryEntity = contentAssetApi.createOrReferencePrimaryEntity(command.userId(), command.blob());
        storedFile.setPrimaryEntityId(primaryEntity.entityId());
        StoredFile savedFile = storedFileRepository.save(storedFile);
        contentAssetApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(savedFile.getId(), primaryEntity.entityId()));
        return toRegisteredContentFile(savedFile);
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
