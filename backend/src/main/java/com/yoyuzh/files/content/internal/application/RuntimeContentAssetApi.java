package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileEntity;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
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
    public FileEntity createOrReferencePrimaryEntity(User user, FileBlob blob) {
        if (fileEntityRepository == null) {
            return createTransientPrimaryEntity(user, blob);
        }

        Optional<FileEntity> existingEntity = fileEntityRepository.findByObjectKeyAndEntityType(
                blob.getObjectKey(),
                FileEntityType.VERSION
        );
        if (existingEntity.isPresent()) {
            FileEntity entity = existingEntity.get();
            entity.setReferenceCount(entity.getReferenceCount() + 1);
            fileEntityRepository.save(entity);
            return entity;
        }

        return fileEntityRepository.save(createTransientPrimaryEntity(user, blob));
    }

    @Override
    public void savePrimaryEntityRelation(StoredFile storedFile, FileEntity primaryEntity) {
        if (storedFileEntityRepository == null) {
            return;
        }

        StoredFileEntity relation = new StoredFileEntity();
        relation.setStoredFile(storedFile);
        relation.setFileEntity(primaryEntity);
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
            FileEntity fileEntity = createOrReferencePrimaryEntity(storedFile.getUser(), blob);
            storedFile.setPrimaryEntity(fileEntity);
            storedFileRepository.save(storedFile);
            savePrimaryEntityRelation(storedFile, fileEntity);
        }
    }

    private FileEntity createTransientPrimaryEntity(User user, FileBlob blob) {
        FileEntity entity = new FileEntity();
        entity.setObjectKey(blob.getObjectKey());
        entity.setContentType(blob.getContentType());
        entity.setSize(blob.getSize());
        entity.setEntityType(FileEntityType.VERSION);
        entity.setReferenceCount(1);
        entity.setCreatedBy(user);
        entity.setStoragePolicyId(resolveDefaultStoragePolicyId());
        return entity;
    }

    private Long resolveDefaultStoragePolicyId() {
        if (storagePolicyQuery == null) {
            return null;
        }
        return storagePolicyQuery.readDefaultPolicyId();
    }
}
