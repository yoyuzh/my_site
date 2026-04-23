package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.domain.StoredFileEntity;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAssetBindingServiceTest {

    @Mock
    private FileEntityRepository fileEntityRepository;

    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;

    @Test
    void shouldCreateTransientPrimaryEntityWhenRepositoryIsUnavailable() {
        ContentAssetBindingService service = new ContentAssetBindingService(null, null, null);

        FileEntity entity = service.createOrReferencePrimaryEntity(7L, createBlob("blobs/blob-1"));

        assertThat(entity.getObjectKey()).isEqualTo("blobs/blob-1");
        assertThat(entity.getEntityType()).isEqualTo(FileEntityType.VERSION);
        assertThat(entity.getReferenceCount()).isEqualTo(1);
        assertThat(entity.getStoragePolicyId()).isNull();
    }

    @Test
    void shouldIncreaseReferenceCountWhenEntityAlreadyExists() {
        ContentAssetBindingService service = new ContentAssetBindingService(fileEntityRepository, null, null);
        FileEntity existing = new FileEntity();
        existing.setObjectKey("blobs/blob-1");
        existing.setEntityType(FileEntityType.VERSION);
        existing.setReferenceCount(2);
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-1", FileEntityType.VERSION))
                .thenReturn(Optional.of(existing));
        when(fileEntityRepository.save(existing)).thenReturn(existing);

        FileEntity entity = service.createOrReferencePrimaryEntity(7L, createBlob("blobs/blob-1"));

        assertThat(entity.getReferenceCount()).isEqualTo(3);
        verify(fileEntityRepository).save(existing);
    }

    @Test
    void shouldSavePrimaryEntityRelationWhenRepositoryAvailable() {
        ContentAssetBindingService service = new ContentAssetBindingService(null, storedFileEntityRepository, null);
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(20L);

        service.savePrimaryEntityRelation(10L, fileEntity);

        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(storedFileEntityRepository).save(captor.capture());
        assertThat(captor.getValue().getStoredFileId()).isEqualTo(10L);
        assertThat(captor.getValue().getFileEntity().getId()).isEqualTo(20L);
        assertThat(captor.getValue().getEntityRole()).isEqualTo("PRIMARY");
    }

    private FileBlob createBlob(String objectKey) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType("text/plain");
        blob.setSize(5L);
        return blob;
    }
}
