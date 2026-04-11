package com.yoyuzh.files.core;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyService;

import java.util.Optional;

final class ContentAssetBindingService {

    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;
    private final StoragePolicyService storagePolicyService;

    ContentAssetBindingService(FileEntityRepository fileEntityRepository,
                               StoredFileEntityRepository storedFileEntityRepository,
                               StoragePolicyService storagePolicyService) {
        this.fileEntityRepository = fileEntityRepository;
        this.storedFileEntityRepository = storedFileEntityRepository;
        this.storagePolicyService = storagePolicyService;
    }

    FileEntity createOrReferencePrimaryEntity(User user, FileBlob blob) {
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
            return fileEntityRepository.save(entity);
        }

        return fileEntityRepository.save(createTransientPrimaryEntity(user, blob));
    }

    StoragePolicyCapabilities resolveDefaultStoragePolicyCapabilities() {
        if (storagePolicyService == null) {
            return null;
        }
        return storagePolicyService.readCapabilities(storagePolicyService.ensureDefaultPolicy());
    }

    void savePrimaryEntityRelation(StoredFile storedFile, FileEntity primaryEntity) {
        if (storedFileEntityRepository == null) {
            return;
        }

        StoredFileEntity relation = new StoredFileEntity();
        relation.setStoredFile(storedFile);
        relation.setFileEntity(primaryEntity);
        relation.setEntityRole("PRIMARY");
        storedFileEntityRepository.save(relation);
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
        if (storagePolicyService == null) {
            return null;
        }
        return storagePolicyService.ensureDefaultPolicy().getId();
    }
}
