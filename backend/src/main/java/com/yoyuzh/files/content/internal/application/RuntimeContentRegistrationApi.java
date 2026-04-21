package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeContentRegistrationApi implements ContentRegistrationApi, ContentDuplicationApi {

    private final StoredFileRepository storedFileRepository;
    private final ContentAssetApi contentAssetApi;

    @Autowired
    public RuntimeContentRegistrationApi(StoredFileRepository storedFileRepository,
                                         FileEntityRepository fileEntityRepository,
                                         StoredFileEntityRepository storedFileEntityRepository,
                                         StoragePolicyQuery storagePolicyQuery) {
        this(
                storedFileRepository,
                new RuntimeContentAssetApi(
                        storedFileRepository,
                        fileEntityRepository,
                        storedFileEntityRepository,
                        storagePolicyQuery
                )
        );
    }

    public RuntimeContentRegistrationApi(StoredFileRepository storedFileRepository,
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
        storedFile.setUser(userReference(command.userId()));
        storedFile.setFilename(command.filename());
        storedFile.setPath(command.normalizedPath());
        storedFile.setContentType(command.contentType());
        storedFile.setSize(command.size());
        storedFile.setDirectory(false);
        storedFile.setBlob(blobReference(command));
        storedFile.setLegacyStorageName(command.blob().objectKey());
        ContentPrimaryEntity primaryEntity = contentAssetApi.createOrReferencePrimaryEntity(command.userId(), command.blob());
        storedFile.setPrimaryEntity(primaryEntityReference(primaryEntity));
        StoredFile savedFile = storedFileRepository.save(storedFile);
        contentAssetApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(savedFile.getId(), primaryEntity.entityId()));
        return toRegisteredContentFile(savedFile);
    }

    private User userReference(Long userId) {
        User user = new User();
        user.setId(userId);
        return user;
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

    private FileBlob blobReference(ContentRegistrationCommand command) {
        FileBlob blob = new FileBlob();
        blob.setId(command.blob().blobId());
        blob.setObjectKey(command.blob().objectKey());
        blob.setContentType(command.blob().contentType());
        blob.setSize(command.blob().size());
        return blob;
    }

    private FileEntity primaryEntityReference(ContentPrimaryEntity primaryEntity) {
        FileEntity entity = new FileEntity();
        entity.setId(primaryEntity.entityId());
        entity.setEntityType(FileEntityType.VERSION);
        entity.setObjectKey(primaryEntity.objectKey());
        entity.setContentType(primaryEntity.contentType());
        entity.setSize(primaryEntity.size());
        entity.setReferenceCount(primaryEntity.referenceCount());
        entity.setStoragePolicyId(primaryEntity.storagePolicyId());
        return entity;
    }
}
