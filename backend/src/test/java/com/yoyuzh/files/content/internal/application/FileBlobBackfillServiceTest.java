package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingApi;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileBlobBackfillServiceTest {

    @Mock
    private WorkspaceContentBindingApi workspaceContentBindingApi;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private FileContentStorage fileContentStorage;

    private FileBlobBackfillService backfillService;

    @BeforeEach
    void setUp() {
        backfillService = new FileBlobBackfillService(workspaceContentBindingApi, fileBlobRepository, fileContentStorage);
    }

    @Test
    void shouldCreateMissingBlobFromLegacyStorageName() {
        WorkspaceContentBindingFile legacyFile = createLegacyFile(10L, 7L, "/docs", "notes.txt");
        when(workspaceContentBindingApi.findFilesMissingBlobBindings()).thenReturn(java.util.List.of(legacyFile));
        when(fileContentStorage.resolveLegacyFileObjectKey(7L, "/docs", "notes.txt")).thenReturn("users/7/docs/notes.txt");
        when(fileBlobRepository.findByObjectKey("users/7/docs/notes.txt")).thenReturn(Optional.empty());
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> {
            FileBlob blob = invocation.getArgument(0);
            blob.setId(100L);
            return blob;
        });

        backfillService.backfillMissingBlobs();

        verify(fileBlobRepository).save(any(FileBlob.class));
        verify(workspaceContentBindingApi).attachBlob(10L, 100L);
    }

    @Test
    void shouldReuseExistingBlobWhenObjectKeyAlreadyBackfilled() {
        WorkspaceContentBindingFile legacyFile = createLegacyFile(11L, 8L, "/docs", "report.pdf");
        FileBlob existingBlob = new FileBlob();
        existingBlob.setId(101L);
        existingBlob.setObjectKey("users/8/docs/report.pdf");
        existingBlob.setContentType("application/pdf");
        existingBlob.setSize(5L);
        when(workspaceContentBindingApi.findFilesMissingBlobBindings()).thenReturn(java.util.List.of(legacyFile));
        when(fileContentStorage.resolveLegacyFileObjectKey(8L, "/docs", "report.pdf")).thenReturn("users/8/docs/report.pdf");
        when(fileBlobRepository.findByObjectKey("users/8/docs/report.pdf")).thenReturn(Optional.of(existingBlob));

        backfillService.backfillMissingBlobs();

        verify(fileBlobRepository, never()).save(any(FileBlob.class));
        verify(workspaceContentBindingApi).attachBlob(11L, 101L);
    }

    private WorkspaceContentBindingFile createLegacyFile(Long id, Long userId, String path, String legacyStorageName) {
        return new WorkspaceContentBindingFile(id, userId, path, legacyStorageName, "application/pdf", 5L, null);
    }
}
