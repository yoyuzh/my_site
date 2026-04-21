package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileEntity;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeContentAssetApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileEntityRepository fileEntityRepository;

    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;

    @Mock
    private StoragePolicyQuery storagePolicyQuery;

    @Test
    void shouldCreateOrReferencePrimaryEntity() {
        RuntimeContentAssetApi api = new RuntimeContentAssetApi(
                storedFileRepository,
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
                new ContentBlobReference(blob.getObjectKey(), blob.getContentType(), blob.getSize())
        );

        assertThat(entity.objectKey()).isEqualTo("blobs/blob-1");
        assertThat(entity.referenceCount()).isEqualTo(1);
        assertThat(entity.storagePolicyId()).isEqualTo(42L);
    }

    @Test
    void shouldSavePrimaryEntityRelation() {
        RuntimeContentAssetApi api = new RuntimeContentAssetApi(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        StoredFile storedFile = new StoredFile();
        storedFile.setId(10L);
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(20L);

        api.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(storedFile.getId(), fileEntity.getId()));

        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(storedFileEntityRepository).save(captor.capture());
        assertThat(captor.getValue().getEntityRole()).isEqualTo("PRIMARY");
    }

    @Test
    void shouldBackfillPrimaryEntities() {
        RuntimeContentAssetApi api = new RuntimeContentAssetApi(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        StoredFile storedFile = createStoredFile(10L, 7L, "notes.txt", createBlob("blobs/blob-20"));
        when(storedFileRepository.findAllByDirectoryFalseAndBlobIsNotNullAndPrimaryEntityIsNull())
                .thenReturn(List.of(storedFile));
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-20", FileEntityType.VERSION))
                .thenReturn(Optional.empty());
        when(fileEntityRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });
        when(storagePolicyQuery.readDefaultPolicyId()).thenReturn(42L);

        api.backfillPrimaryEntities();

        assertThat(storedFile.getPrimaryEntity()).isNotNull();
        assertThat(storedFile.getPrimaryEntity().getObjectKey()).isEqualTo("blobs/blob-20");
        verify(storedFileRepository).save(storedFile);
        verify(storedFileEntityRepository).save(any(StoredFileEntity.class));
    }

    private StoredFile createStoredFile(Long id, Long userId, String filename, FileBlob blob) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(createUser(userId));
        file.setPath("/docs");
        file.setFilename(filename);
        file.setBlob(blob);
        file.setContentType(blob.getContentType());
        file.setSize(blob.getSize());
        file.setDirectory(false);
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }

    private FileBlob createBlob(String objectKey) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType("text/plain");
        blob.setSize(5L);
        return blob;
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
