package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationApi;
import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationInspection;
import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationItem;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RuntimeContentStoragePolicyMigrationApi implements ContentStoragePolicyMigrationApi {

    private final FileEntityRepository fileEntityRepository;
    private final FileBlobRepository fileBlobRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;

    public RuntimeContentStoragePolicyMigrationApi(FileEntityRepository fileEntityRepository,
                                                   FileBlobRepository fileBlobRepository,
                                                   StoredFileEntityRepository storedFileEntityRepository) {
        this.fileEntityRepository = fileEntityRepository;
        this.fileBlobRepository = fileBlobRepository;
        this.storedFileEntityRepository = storedFileEntityRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentStoragePolicyMigrationItem> listVersionItemsByStoragePolicyId(Long storagePolicyId) {
        return fileEntityRepository.findByStoragePolicyIdAndEntityTypeOrderByIdAsc(
                        storagePolicyId,
                        FileEntityType.VERSION
                )
                .stream()
                .map(this::toMigrationItem)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ContentStoragePolicyMigrationInspection inspectVersionItemsByStoragePolicyId(Long storagePolicyId) {
        return new ContentStoragePolicyMigrationInspection(
                fileEntityRepository.countByStoragePolicyIdAndEntityType(storagePolicyId, FileEntityType.VERSION),
                storedFileEntityRepository.countDistinctStoredFilesByStoragePolicyIdAndEntityType(
                        storagePolicyId,
                        FileEntityType.VERSION
                ),
                VERSION_ENTITY_TYPE
        );
    }

    @Override
    @Transactional
    public void reassignVersionItem(Long entityId, Long blobId, Long targetStoragePolicyId, String nextObjectKey) {
        FileEntity entity = fileEntityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalStateException("storage policy migration entity not found"));
        if (entity.getEntityType() != FileEntityType.VERSION) {
            throw new IllegalStateException("storage policy migration only supports version entities");
        }
        FileBlob blob = fileBlobRepository.findById(blobId)
                .orElseThrow(() -> new IllegalStateException("storage policy migration blob not found"));
        entity.setObjectKey(nextObjectKey);
        entity.setStoragePolicyId(targetStoragePolicyId);
        fileEntityRepository.save(entity);

        blob.setObjectKey(nextObjectKey);
        fileBlobRepository.save(blob);
    }

    private ContentStoragePolicyMigrationItem toMigrationItem(FileEntity entity) {
        FileBlob blob = fileBlobRepository.findByObjectKey(entity.getObjectKey())
                .orElseThrow(() -> new IllegalStateException("storage policy migration blob not found"));
        return new ContentStoragePolicyMigrationItem(
                entity.getId(),
                entity.getObjectKey(),
                entity.getSize(),
                entity.getContentType(),
                blob.getId(),
                blob.getContentType(),
                blob.getSize(),
                storedFileEntityRepository.countByFileEntityId(entity.getId()),
                VERSION_ENTITY_TYPE
        );
    }
}
