package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeContentRegistrationApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileEntityRepository fileEntityRepository;

    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;

    @Mock
    private StoragePolicyQuery storagePolicyQuery;

    @Test
    void shouldRegisterBlobAndPrimaryEntity() {
        RuntimeContentRegistrationApi api = new RuntimeContentRegistrationApi(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        User user = createUser(7L);
        FileBlob blob = createBlob("blobs/blob-1");
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-1", FileEntityType.VERSION))
                .thenReturn(Optional.empty());
        when(fileEntityRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(20L);
            return entity;
        });
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            storedFile.setId(10L);
            return storedFile;
        });
        when(storagePolicyQuery.readDefaultPolicyId()).thenReturn(42L);

        RegisteredContentFile response = api.registerBlob(new ContentRegistrationCommand(
                user.getId(),
                "/docs",
                "notes.txt",
                "text/plain",
                5L,
                new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize())
        ));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.filename()).isEqualTo("notes.txt");
        verify(fileEntityRepository).findByObjectKeyAndEntityType("blobs/blob-1", FileEntityType.VERSION);
        verify(storedFileEntityRepository).save(any());
    }

    @Test
    void shouldReuseExistingPrimaryEntityWhenBlobAlreadyKnown() {
        RuntimeContentRegistrationApi api = new RuntimeContentRegistrationApi(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        User user = createUser(7L);
        FileBlob blob = createBlob("blobs/blob-2");
        FileEntity existingEntity = new FileEntity();
        existingEntity.setId(30L);
        existingEntity.setObjectKey("blobs/blob-2");
        existingEntity.setEntityType(FileEntityType.VERSION);
        existingEntity.setReferenceCount(2);
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-2", FileEntityType.VERSION))
                .thenReturn(Optional.of(existingEntity));
        when(fileEntityRepository.save(existingEntity)).thenReturn(existingEntity);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        api.registerBlob(new ContentRegistrationCommand(
                user.getId(),
                "/docs",
                "report.pdf",
                "application/pdf",
                12L,
                new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize())
        ));

        assertThat(existingEntity.getReferenceCount()).isEqualTo(3);
        verify(fileEntityRepository).save(existingEntity);
    }

    @Test
    void shouldDuplicateBlobBackedFileThroughContentSeam() {
        RuntimeContentRegistrationApi api = new RuntimeContentRegistrationApi(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        User user = createUser(7L);
        FileBlob blob = createBlob("blobs/blob-copy");
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-copy", FileEntityType.VERSION))
                .thenReturn(Optional.empty());
        when(fileEntityRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(21L);
            return entity;
        });
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            storedFile.setId(11L);
            return storedFile;
        });
        when(storagePolicyQuery.readDefaultPolicyId()).thenReturn(42L);

        RegisteredContentFile response = api.duplicateBlobBackedFile(new ContentRegistrationCommand(
                user.getId(),
                "/downloads",
                "notes-copy.txt",
                "text/plain",
                5L,
                new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize())
        ));

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.path()).isEqualTo("/downloads");
        verify(storedFileEntityRepository).save(any());
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setCreatedAt(LocalDateTime.now());
        return user;
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
