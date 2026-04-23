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
import com.yoyuzh.files.content.internal.domain.StoredFileEntity;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingApi;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingFile;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public final class RuntimeContentAssetApi implements ContentAssetApi {

    static final String PRIMARY_ENTITY_ROLE = "PRIMARY";

    private final WorkspaceContentBindingApi workspaceContentBindingApi;
    private final FileBlobRepository fileBlobRepository;
    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;
    private final StoragePolicyQuery storagePolicyQuery;

    public RuntimeContentAssetApi(WorkspaceContentBindingApi workspaceContentBindingApi,
                                  FileEntityRepository fileEntityRepository,
                                  StoredFileEntityRepository storedFileEntityRepository,
                                  StoragePolicyQuery storagePolicyQuery) {
        this(
                workspaceContentBindingApi,
                null,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
    }

    @Autowired
    public RuntimeContentAssetApi(WorkspaceContentBindingApi workspaceContentBindingApi,
                                  FileBlobRepository fileBlobRepository,
                                  FileEntityRepository fileEntityRepository,
                                  StoredFileEntityRepository storedFileEntityRepository,
                                  StoragePolicyQuery storagePolicyQuery) {
        this.workspaceContentBindingApi = workspaceContentBindingApi;
        this.fileBlobRepository = fileBlobRepository;
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
        relation.setStoredFileId(command.storedFileId());
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
        if (workspaceContentBindingApi == null || fileEntityRepository == null || fileBlobRepository == null) {
            return;
        }

        for (WorkspaceContentBindingFile storedFile : workspaceContentBindingApi.findFilesMissingPrimaryEntityBindings()) {
            FileBlob blob = fileBlobRepository.findById(storedFile.blobId()).orElse(null);
            if (blob == null) {
                continue;
            }
            ContentPrimaryEntity primaryEntity = createOrReferencePrimaryEntity(
                    storedFile.userId(),
                    toBlobReference(blob)
            );
            workspaceContentBindingApi.attachPrimaryEntity(storedFile.fileId(), primaryEntity.entityId());
            savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(storedFile.fileId(), primaryEntity.entityId()));
        }
    }

    private FileEntity createTransientPrimaryEntity(Long userId, ContentBlobReference blob) {
        FileEntity entity = new FileEntity();
        entity.setObjectKey(blob.objectKey());
        entity.setContentType(blob.contentType());
        entity.setSize(blob.size());
        entity.setEntityType(FileEntityType.VERSION);
        entity.setReferenceCount(1);
        entity.setCreatedByUserId(userId);
        entity.setStoragePolicyId(resolveDefaultStoragePolicyId());
        return entity;
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

    private FileEntity fileEntityReference(Long primaryEntityId) {
        FileEntity entity = new FileEntity();
        entity.setId(primaryEntityId);
        entity.setEntityType(FileEntityType.VERSION);
        return entity;
    }
}
