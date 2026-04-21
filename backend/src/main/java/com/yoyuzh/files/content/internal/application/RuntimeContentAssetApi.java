package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.content.internal.domain.StoredFileEntity;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public final class RuntimeContentAssetApi implements ContentAssetApi {

    static final String PRIMARY_ENTITY_ROLE = "PRIMARY";

    private final StoredFileRepository storedFileRepository;
    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;
    private final StoragePolicyQuery storagePolicyQuery;

    public RuntimeContentAssetApi(StoredFileRepository storedFileRepository,
                                  FileEntityRepository fileEntityRepository,
                                  StoredFileEntityRepository storedFileEntityRepository,
                                  StoragePolicyQuery storagePolicyQuery) {
        this.storedFileRepository = storedFileRepository;
        this.fileEntityRepository = fileEntityRepository;
        this.storedFileEntityRepository = storedFileEntityRepository;
        this.storagePolicyQuery = storagePolicyQuery;
    }

    @Override
    public ContentPrimaryEntity createOrReferencePrimaryEntity(Long userId, ContentBlobReference blob) {
        if (fileEntityRepository == null) {
            return toContentPrimaryEntity(createTransientPrimaryEntity(userId, blob));
        }

        Optional<FileEntity> existingEntity = fileEntityRepository.findByObjectKeyAndEntityType(
                blob.objectKey(),
                FileEntityType.VERSION
        );
        if (existingEntity.isPresent()) {
            FileEntity entity = existingEntity.get();
            entity.setReferenceCount(entity.getReferenceCount() + 1);
            fileEntityRepository.save(entity);
            return toContentPrimaryEntity(entity);
        }

        return toContentPrimaryEntity(fileEntityRepository.save(createTransientPrimaryEntity(userId, blob)));
    }

    @Override
    public void savePrimaryEntityRelation(ContentPrimaryEntityRelationCommand command) {
        if (storedFileEntityRepository == null) {
            return;
        }

        StoredFileEntity relation = new StoredFileEntity();
        relation.setStoredFile(storedFileReference(command.storedFileId()));
        relation.setFileEntity(fileEntityReference(command.primaryEntityId()));
        relation.setEntityRole(PRIMARY_ENTITY_ROLE);
        storedFileEntityRepository.save(relation);
    }

    @Override
    public StoragePolicyCapabilities resolveDefaultStoragePolicyCapabilities() {
        if (storagePolicyQuery == null) {
            return null;
        }
        return storagePolicyQuery.readDefaultPolicySnapshot().capabilities();
    }

    @Override
    public void backfillPrimaryEntities() {
        if (storedFileRepository == null || fileEntityRepository == null) {
            return;
        }

        for (StoredFile storedFile : storedFileRepository.findAllByDirectoryFalseAndBlobIsNotNullAndPrimaryEntityIsNull()) {
            FileBlob blob = storedFile.getBlob();
            ContentPrimaryEntity primaryEntity = createOrReferencePrimaryEntity(
                    storedFile.getUser().getId(),
                    toBlobReference(blob)
            );
            storedFile.setPrimaryEntity(legacyPrimaryEntityReference(primaryEntity));
            storedFileRepository.save(storedFile);
            savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(storedFile.getId(), primaryEntity.entityId()));
        }
    }

    private FileEntity createTransientPrimaryEntity(Long userId, ContentBlobReference blob) {
        FileEntity entity = new FileEntity();
        entity.setObjectKey(blob.objectKey());
        entity.setContentType(blob.contentType());
        entity.setSize(blob.size());
        entity.setEntityType(FileEntityType.VERSION);
        entity.setReferenceCount(1);
        entity.setCreatedBy(userReference(userId));
        entity.setStoragePolicyId(resolveDefaultStoragePolicyId());
        return entity;
    }

    private User userReference(Long userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }

    private Long resolveDefaultStoragePolicyId() {
        if (storagePolicyQuery == null) {
            return null;
        }
        return storagePolicyQuery.readDefaultPolicyId();
    }

    private ContentBlobReference toBlobReference(FileBlob blob) {
        return new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize());
    }

    private ContentPrimaryEntity toContentPrimaryEntity(FileEntity entity) {
        return new ContentPrimaryEntity(
                entity.getId(),
                entity.getObjectKey(),
                entity.getContentType(),
                entity.getSize() == null ? 0L : entity.getSize(),
                entity.getReferenceCount() == null ? 0 : entity.getReferenceCount(),
                entity.getStoragePolicyId()
        );
    }

    private StoredFile storedFileReference(Long storedFileId) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(storedFileId);
        return storedFile;
    }

    private FileEntity fileEntityReference(Long primaryEntityId) {
        FileEntity entity = new FileEntity();
        entity.setId(primaryEntityId);
        entity.setEntityType(FileEntityType.VERSION);
        return entity;
    }

    private FileEntity legacyPrimaryEntityReference(ContentPrimaryEntity primaryEntity) {
        FileEntity entity = fileEntityReference(primaryEntity.entityId());
        entity.setObjectKey(primaryEntity.objectKey());
        entity.setContentType(primaryEntity.contentType());
        entity.setSize(primaryEntity.size());
        entity.setReferenceCount(primaryEntity.referenceCount());
        entity.setStoragePolicyId(primaryEntity.storagePolicyId());
        return entity;
    }
}
