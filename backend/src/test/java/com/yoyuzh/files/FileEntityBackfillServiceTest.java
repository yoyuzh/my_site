package com.yoyuzh.files;

import com.yoyuzh.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileEntityBackfillServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileEntityRepository fileEntityRepository;
    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;

    private FileEntityBackfillService backfillService;

    @BeforeEach
    void setUp() {
        backfillService = new FileEntityBackfillService(
                storedFileRepository,
                fileEntityRepository,
                storedFileEntityRepository
        );
    }

    @Test
    void shouldBackfillPrimaryEntityFromExistingBlob() {
        StoredFile storedFile = createStoredFile(10L, 7L, "notes.txt", createBlob(20L, "blobs/blob-20"));
        when(storedFileRepository.findAllByDirectoryFalseAndBlobIsNotNullAndPrimaryEntityIsNull())
                .thenReturn(List.of(storedFile));
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-20", FileEntityType.VERSION))
                .thenReturn(Optional.empty());
        when(fileEntityRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });

        backfillService.backfillPrimaryEntities();

        assertThat(storedFile.getPrimaryEntity()).isNotNull();
        assertThat(storedFile.getPrimaryEntity().getObjectKey()).isEqualTo("blobs/blob-20");
        assertThat(storedFile.getPrimaryEntity().getEntityType()).isEqualTo(FileEntityType.VERSION);
        assertThat(storedFile.getPrimaryEntity().getReferenceCount()).isEqualTo(1);
        verify(fileEntityRepository).save(any(FileEntity.class));
        verify(storedFileRepository).save(storedFile);
        verify(storedFileEntityRepository).save(any(StoredFileEntity.class));
    }

    @Test
    void shouldReuseExistingFileEntityWhenBackfillRunsAgain() {
        StoredFile storedFile = createStoredFile(11L, 8L, "report.pdf", createBlob(21L, "blobs/blob-21"));
        FileEntity existingEntity = new FileEntity();
        existingEntity.setId(101L);
        existingEntity.setObjectKey("blobs/blob-21");
        existingEntity.setEntityType(FileEntityType.VERSION);
        existingEntity.setReferenceCount(3);
        when(storedFileRepository.findAllByDirectoryFalseAndBlobIsNotNullAndPrimaryEntityIsNull())
                .thenReturn(List.of(storedFile));
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-21", FileEntityType.VERSION))
                .thenReturn(Optional.of(existingEntity));

        backfillService.backfillPrimaryEntities();

        assertThat(storedFile.getPrimaryEntity()).isSameAs(existingEntity);
        assertThat(existingEntity.getReferenceCount()).isEqualTo(4);
        verify(fileEntityRepository).save(existingEntity);
        verify(storedFileRepository).save(storedFile);
        verify(storedFileEntityRepository).save(any(StoredFileEntity.class));
    }

    private StoredFile createStoredFile(Long id, Long userId, String filename, FileBlob blob) {
        User user = new User();
        user.setId(userId);
        user.setUsername("user-" + userId);

        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(user);
        file.setPath("/docs");
        file.setFilename(filename);
        file.setBlob(blob);
        file.setContentType(blob.getContentType());
        file.setSize(blob.getSize());
        file.setDirectory(false);
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }

    private FileBlob createBlob(Long id, String objectKey) {
        FileBlob blob = new FileBlob();
        blob.setId(id);
        blob.setObjectKey(objectKey);
        blob.setContentType("text/plain");
        blob.setSize(5L);
        blob.setCreatedAt(LocalDateTime.now());
        return blob;
    }
}
