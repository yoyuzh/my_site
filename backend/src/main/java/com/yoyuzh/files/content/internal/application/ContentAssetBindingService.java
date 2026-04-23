package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;

final class ContentAssetBindingService {

    private final ContentAssetApi contentAssetApi;

    ContentAssetBindingService(FileEntityRepository fileEntityRepository,
                               StoredFileEntityRepository storedFileEntityRepository,
                               StoragePolicyQuery storagePolicyQuery) {
        this(null, fileEntityRepository, storedFileEntityRepository, storagePolicyQuery);
    }

    ContentAssetBindingService(FileBlobRepository fileBlobRepository,
                               FileEntityRepository fileEntityRepository,
                               StoredFileEntityRepository storedFileEntityRepository,
                               StoragePolicyQuery storagePolicyQuery) {
        this.contentAssetApi = new RuntimeContentAssetApi(
                null,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
    }

    FileEntity createOrReferencePrimaryEntity(Long userId, FileBlob blob) {
        return toLegacyEntity(contentAssetApi.createOrReferencePrimaryEntity(
                userId,
                new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize())
        ));
    }

    StoragePolicyCapabilities resolveDefaultStoragePolicyCapabilities() {
        return contentAssetApi.resolveDefaultStoragePolicyCapabilities();
    }

    void savePrimaryEntityRelation(Long storedFileId, FileEntity primaryEntity) {
        contentAssetApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(
                storedFileId,
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
