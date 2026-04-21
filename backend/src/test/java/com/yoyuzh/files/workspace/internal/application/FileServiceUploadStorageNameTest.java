package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.internal.application.*;
import com.yoyuzh.files.workspace.internal.domain.*;
import com.yoyuzh.files.workspace.internal.infra.*;
import com.yoyuzh.files.workspace.internal.web.*;
import com.yoyuzh.files.content.internal.application.*;
import com.yoyuzh.files.content.internal.domain.*;
import com.yoyuzh.files.content.internal.infra.*;

import com.yoyuzh.ops.admin.internal.application.AdminMetricsService;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceUploadStorageNameTest {

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private FileContentStorage fileContentStorage;
    @Mock
    private AdminMetricsService adminMetricsService;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxFileSize(500L * 1024 * 1024);
        fileService = new FileService(
                storedFileRepository,
                fileBlobRepository,
                fileContentStorage,
                adminMetricsService,
                properties
        );
    }

    @Test
    void shouldPersistUploadedFileStorageName() {
        User user = createUser(1L);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes()
        );
        when(storedFileRepository.existsByUserIdAndPathAndFilename(1L, "/", "notes.txt")).thenReturn(false);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> {
            FileBlob blob = invocation.getArgument(0);
            blob.setId(100L);
            return blob;
        });
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            storedFile.setId(10L);
            return storedFile;
        });

        fileService.upload(user, "/", multipartFile);

        ArgumentCaptor<StoredFile> storedFileCaptor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileRepository).save(storedFileCaptor.capture());
        StoredFile savedFile = storedFileCaptor.getValue();
        assertThat(savedFile.getLegacyStorageName()).startsWith("blobs/");
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.com");
        user.setPasswordHash("encoded");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
