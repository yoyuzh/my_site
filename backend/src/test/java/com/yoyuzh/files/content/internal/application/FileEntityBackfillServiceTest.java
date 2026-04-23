package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.domain.StoredFileEntity;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingApi;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingFile;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
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
    private WorkspaceContentBindingApi workspaceContentBindingApi;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private FileEntityRepository fileEntityRepository;
    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;
    @Mock
    private StoragePolicyQuery storagePolicyQuery;

    private FileEntityBackfillService backfillService;

    @BeforeEach
    void setUp() {
        backfillService = new FileEntityBackfillService(
                workspaceContentBindingApi,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
    }

    @Test
    void shouldBackfillPrimaryEntityFromExistingBlob() {
        WorkspaceContentBindingFile storedFile = createStoredFile(10L, 7L, createBlob(20L, "blobs/blob-20"));
        when(workspaceContentBindingApi.findFilesMissingPrimaryEntityBindings())
                .thenReturn(List.of(storedFile));
        when(fileBlobRepository.findById(20L)).thenReturn(Optional.of(createBlob(20L, "blobs/blob-20")));
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-20", FileEntityType.VERSION))
                .thenReturn(Optional.empty());
        when(fileEntityRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });
        when(storagePolicyQuery.readDefaultPolicyId()).thenReturn(42L);

        backfillService.backfillPrimaryEntities();

        verify(fileEntityRepository).save(any(FileEntity.class));
        verify(workspaceContentBindingApi).attachPrimaryEntity(10L, 100L);
        verify(storedFileEntityRepository).save(any(StoredFileEntity.class));
    }

    @Test
    void shouldReuseExistingFileEntityWhenBackfillRunsAgain() {
        WorkspaceContentBindingFile storedFile = createStoredFile(11L, 8L, createBlob(21L, "blobs/blob-21"));
        FileEntity existingEntity = new FileEntity();
        existingEntity.setId(101L);
        existingEntity.setObjectKey("blobs/blob-21");
        existingEntity.setEntityType(FileEntityType.VERSION);
        existingEntity.setReferenceCount(3);
        when(workspaceContentBindingApi.findFilesMissingPrimaryEntityBindings())
                .thenReturn(List.of(storedFile));
        when(fileBlobRepository.findById(21L)).thenReturn(Optional.of(createBlob(21L, "blobs/blob-21")));
        when(fileEntityRepository.findByObjectKeyAndEntityType("blobs/blob-21", FileEntityType.VERSION))
                .thenReturn(Optional.of(existingEntity));

        backfillService.backfillPrimaryEntities();

        assertThat(existingEntity.getReferenceCount()).isEqualTo(4);
        verify(fileEntityRepository).save(existingEntity);
        verify(workspaceContentBindingApi).attachPrimaryEntity(11L, existingEntity.getId());
        verify(storedFileEntityRepository).save(any(StoredFileEntity.class));
    }

    private WorkspaceContentBindingFile createStoredFile(Long id, Long userId, FileBlob blob) {
        return new WorkspaceContentBindingFile(id, userId, "/docs", null, blob.getContentType(), blob.getSize(), blob.getId());
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
