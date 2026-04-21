package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;

final class ContentAssetBindingService {

    private final ContentAssetApi contentAssetApi;

    ContentAssetBindingService(FileEntityRepository fileEntityRepository,
                               StoredFileEntityRepository storedFileEntityRepository,
                               StoragePolicyQuery storagePolicyQuery) {
        this.contentAssetApi = new RuntimeContentAssetApi(
                null,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
    }

    FileEntity createOrReferencePrimaryEntity(User user, FileBlob blob) {
        return toLegacyEntity(contentAssetApi.createOrReferencePrimaryEntity(
                user.getId(),
                new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize())
        ));
    }

    StoragePolicyCapabilities resolveDefaultStoragePolicyCapabilities() {
        return contentAssetApi.resolveDefaultStoragePolicyCapabilities();
    }

    void savePrimaryEntityRelation(StoredFile storedFile, FileEntity primaryEntity) {
        contentAssetApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(
                storedFile.getId(),
                primaryEntity.getId()
        ));
    }

    private FileEntity toLegacyEntity(ContentPrimaryEntity entity) {
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(entity.entityId());
        fileEntity.setObjectKey(entity.objectKey());
        fileEntity.setContentType(entity.contentType());
        fileEntity.setSize(entity.size());
        fileEntity.setEntityType(FileEntityType.VERSION);
        fileEntity.setReferenceCount(entity.referenceCount());
        fileEntity.setStoragePolicyId(entity.storagePolicyId());
        return fileEntity;
    }
}
