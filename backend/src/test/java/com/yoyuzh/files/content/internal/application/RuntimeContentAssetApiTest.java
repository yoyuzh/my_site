package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.domain.StoredFileEntity;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingApi;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingFile;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeContentAssetApiTest {

    @Mock
    private WorkspaceContentBindingApi workspaceContentBindingApi;

    @Mock
    private com.yoyuzh.files.content.internal.infra.FileBlobRepository fileBlobRepository;

    @Mock
    private FileEntityRepository fileEntityRepository;

    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;

    @Mock
    private StoragePolicyQuery storagePolicyQuery;

    @Test
    void shouldCreateOrReferencePrimaryEntity() {
        RuntimeContentAssetApi api = new RuntimeContentAssetApi(
                workspaceContentBindingApi,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        FileBlob blob = createBlob("blobs/blob-1");
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-1", FileEntityType.VERSION))
                .thenReturn(Optional.empty());
        when(fileEntityRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storagePolicyQuery.readDefaultPolicyId()).thenReturn(42L);

        ContentPrimaryEntity entity = api.createOrReferencePrimaryEntity(
                7L,
                new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize())
        );

        assertThat(entity.objectKey()).isEqualTo("blobs/blob-1");
        assertThat(entity.referenceCount()).isEqualTo(1);
        assertThat(entity.storagePolicyId()).isEqualTo(42L);
    }

    @Test
    void shouldSavePrimaryEntityRelation() {
        RuntimeContentAssetApi api = new RuntimeContentAssetApi(
                workspaceContentBindingApi,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(20L);

        api.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(10L, fileEntity.getId()));

        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(storedFileEntityRepository).save(captor.capture());
        assertThat(captor.getValue().getEntityRole()).isEqualTo("PRIMARY");
    }

    @Test
    void shouldBackfillPrimaryEntities() {
        RuntimeContentAssetApi api = new RuntimeContentAssetApi(
                workspaceContentBindingApi,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        WorkspaceContentBindingFile storedFile = createStoredFile(10L, 7L, createBlob("blobs/blob-20"));
        when(workspaceContentBindingApi.findFilesMissingPrimaryEntityBindings())
                .thenReturn(List.of(storedFile));
        when(fileBlobRepository.findById(99L)).thenReturn(Optional.of(createBlob("blobs/blob-20")));
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-20", FileEntityType.VERSION))
                .thenReturn(Optional.empty());
        when(fileEntityRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });
        when(storagePolicyQuery.readDefaultPolicyId()).thenReturn(42L);

        api.backfillPrimaryEntities();

        verify(workspaceContentBindingApi).attachPrimaryEntity(10L, 100L);
        verify(storedFileEntityRepository).save(any(StoredFileEntity.class));
    }

    private WorkspaceContentBindingFile createStoredFile(Long id, Long userId, FileBlob blob) {
        return new WorkspaceContentBindingFile(id, userId, "/docs", null, blob.getContentType(), blob.getSize(), blob.getId());
    }

    private FileBlob createBlob(String objectKey) {
        FileBlob blob = new FileBlob();
        blob.setId(99L);
        blob.setObjectKey(objectKey);
        blob.setContentType("text/plain");
        blob.setSize(5L);
        return blob;
    }
}
