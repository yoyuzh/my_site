package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.workspace.internal.application.*;
import com.yoyuzh.files.workspace.internal.domain.*;
import com.yoyuzh.files.workspace.internal.infra.*;
import com.yoyuzh.files.workspace.internal.web.*;
import com.yoyuzh.files.content.internal.application.*;
import com.yoyuzh.files.content.internal.domain.*;
import com.yoyuzh.files.content.internal.infra.*;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileBlobBackfillServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private FileContentStorage fileContentStorage;

    private FileBlobBackfillService backfillService;

    @BeforeEach
    void setUp() {
        backfillService = new FileBlobBackfillService(storedFileRepository, fileBlobRepository, fileContentStorage);
    }

    @Test
    void shouldCreateMissingBlobFromLegacyStorageName() {
        StoredFile legacyFile = createLegacyFile(10L, 7L, "/docs", "notes.txt", "notes.txt");
        when(storedFileRepository.findAllByDirectoryFalseAndBlobIsNull()).thenReturn(java.util.List.of(legacyFile));
        when(fileContentStorage.resolveLegacyFileObjectKey(7L, "/docs", "notes.txt")).thenReturn("users/7/docs/notes.txt");
        when(fileBlobRepository.findByObjectKey("users/7/docs/notes.txt")).thenReturn(Optional.empty());
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> {
            FileBlob blob = invocation.getArgument(0);
            blob.setId(100L);
            return blob;
        });

        backfillService.backfillMissingBlobs();

        verify(fileBlobRepository).save(any(FileBlob.class));
        verify(storedFileRepository).save(legacyFile);
    }

    @Test
    void shouldReuseExistingBlobWhenObjectKeyAlreadyBackfilled() {
        StoredFile legacyFile = createLegacyFile(11L, 8L, "/docs", "report.pdf", "report.pdf");
        FileBlob existingBlob = new FileBlob();
        existingBlob.setId(101L);
        existingBlob.setObjectKey("users/8/docs/report.pdf");
        existingBlob.setContentType("application/pdf");
        existingBlob.setSize(5L);
        when(storedFileRepository.findAllByDirectoryFalseAndBlobIsNull()).thenReturn(java.util.List.of(legacyFile));
        when(fileContentStorage.resolveLegacyFileObjectKey(8L, "/docs", "report.pdf")).thenReturn("users/8/docs/report.pdf");
        when(fileBlobRepository.findByObjectKey("users/8/docs/report.pdf")).thenReturn(Optional.of(existingBlob));

        backfillService.backfillMissingBlobs();

        verify(fileBlobRepository, never()).save(any(FileBlob.class));
        verify(storedFileRepository).save(legacyFile);
    }

    private StoredFile createLegacyFile(Long id, Long userId, String path, String filename, String legacyStorageName) {
        User user = new User();
        user.setId(userId);
        user.setUsername("user-" + userId);

        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(user);
        file.setPath(path);
        file.setFilename(filename);
        file.setLegacyStorageName(legacyStorageName);
        file.setContentType("application/pdf");
        file.setSize(5L);
        file.setDirectory(false);
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }
}
