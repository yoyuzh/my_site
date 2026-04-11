package com.yoyuzh.files.core;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentBlobLifecycleServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileBlobRepository fileBlobRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Test
    void shouldCreateAndSaveBlob() {
        ContentBlobLifecycleService service = createService();
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileBlob saved = service.createAndSaveBlob("blobs/blob-1", "text/plain", 5L);

        assertThat(saved.getObjectKey()).isEqualTo("blobs/blob-1");
        assertThat(saved.getContentType()).isEqualTo("text/plain");
        assertThat(saved.getSize()).isEqualTo(5L);
        verify(fileBlobRepository).save(any(FileBlob.class));
    }

    @Test
    void shouldDeleteBlobWhenOperationFailsAfterWrite() {
        ContentBlobLifecycleService service = createService();

        assertThatThrownBy(() -> service.executeAfterBlobStored("blobs/blob-1", () -> {
            throw new IllegalStateException("write-failed");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("write-failed");

        verify(fileContentStorage).deleteBlob("blobs/blob-1");
    }

    @Test
    void shouldKeepCleanupFailuresAsSuppressedException() {
        ContentBlobLifecycleService service = createService();
        doThrow(new IllegalStateException("cleanup-failed"))
                .when(fileContentStorage).deleteBlob("blobs/blob-1");

        Throwable thrown = null;
        try {
            service.executeAfterBlobStored("blobs/blob-1", () -> {
                throw new IllegalStateException("write-failed");
            });
        } catch (Throwable ex) {
            thrown = ex;
        }

        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("write-failed");
        assertThat(thrown).isNotNull();
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0])
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cleanup-failed");
    }

    @Test
    void shouldCollectOnlyUnreferencedBlobsAfterDeletion() {
        ContentBlobLifecycleService service = createService();
        FileBlob onlyReferencedByDeletedFiles = createBlob(10L, "blobs/blob-10");
        FileBlob stillReferencedElsewhere = createBlob(20L, "blobs/blob-20");
        StoredFile fileA = createStoredFile(false, onlyReferencedByDeletedFiles);
        StoredFile fileB = createStoredFile(false, onlyReferencedByDeletedFiles);
        StoredFile fileC = createStoredFile(false, stillReferencedElsewhere);

        when(storedFileRepository.countByBlobId(10L)).thenReturn(2L);
        when(storedFileRepository.countByBlobId(20L)).thenReturn(3L);

        List<FileBlob> blobsToDelete = service.collectBlobsToDelete(List.of(fileA, fileB, fileC));

        assertThat(blobsToDelete).containsExactly(onlyReferencedByDeletedFiles);
    }

    @Test
    void shouldDeleteBlobObjectAndMetadata() {
        ContentBlobLifecycleService service = createService();
        FileBlob blob = createBlob(10L, "blobs/blob-10");

        service.deleteBlobs(List.of(blob));

        verify(fileContentStorage).deleteBlob("blobs/blob-10");
        verify(fileBlobRepository).delete(blob);
    }

    @Test
    void shouldReturnRequiredBlobForRegularFile() {
        ContentBlobLifecycleService service = createService();
        FileBlob blob = createBlob(10L, "blobs/blob-10");
        StoredFile storedFile = createStoredFile(false, blob);

        FileBlob resolved = service.getRequiredBlob(storedFile);

        assertThat(resolved).isSameAs(blob);
    }

    @Test
    void shouldRejectMissingBlobForDirectoryOrDetachedFile() {
        ContentBlobLifecycleService service = createService();
        StoredFile directory = createStoredFile(true, null);
        StoredFile detachedFile = createStoredFile(false, null);

        assertThatThrownBy(() -> service.getRequiredBlob(directory)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getRequiredBlob(detachedFile)).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldCleanupWrittenBlobListOnFailure() {
        ContentBlobLifecycleService service = createService();
        RuntimeException operationFailure = new RuntimeException("import-failed");
        doAnswer(invocation -> {
            String objectKey = invocation.getArgument(0);
            if ("blobs/blob-2".equals(objectKey)) {
                throw new IllegalStateException("cleanup-failed");
            }
            return null;
        }).when(fileContentStorage).deleteBlob(anyString());

        service.cleanupWrittenBlobs(List.of("blobs/blob-1", "blobs/blob-2"), operationFailure);

        verify(fileContentStorage).deleteBlob("blobs/blob-1");
        verify(fileContentStorage).deleteBlob("blobs/blob-2");
        assertThat(operationFailure.getSuppressed()).hasSize(1);
        assertThat(operationFailure.getSuppressed()[0])
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cleanup-failed");
    }

    private ContentBlobLifecycleService createService() {
        return new ContentBlobLifecycleService(storedFileRepository, fileBlobRepository, fileContentStorage);
    }

    private StoredFile createStoredFile(boolean directory, FileBlob blob) {
        StoredFile storedFile = new StoredFile();
        storedFile.setDirectory(directory);
        storedFile.setBlob(blob);
        return storedFile;
    }

    private FileBlob createBlob(Long id, String objectKey) {
        FileBlob blob = new FileBlob();
        blob.setId(id);
        blob.setObjectKey(objectKey);
        blob.setContentType("text/plain");
        blob.setSize(1L);
        return blob;
    }
}
