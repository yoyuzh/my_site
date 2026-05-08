package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.internal.application.*;
import com.yoyuzh.files.workspace.internal.domain.*;
import com.yoyuzh.files.workspace.internal.infra.*;
import com.yoyuzh.files.content.internal.application.*;
import com.yoyuzh.files.content.internal.domain.*;
import com.yoyuzh.files.content.internal.infra.*;

import com.yoyuzh.ops.admin.internal.application.AdminMetricsService;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadOptions;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.files.content.api.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceMkdirStorageNameTest {

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
        fileService = FileServiceTestSupport.create(
                storedFileRepository,
                fileBlobRepository,
                fileContentStorage,
                adminMetricsService,
                WorkspaceDownloadOptions.disabled(),
                properties.getMaxFileSize()
        );
    }

    @Test
    void shouldPersistDirectoryStorageNameWhenCreatingDirectory() {
        User user = createUser(1L);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            storedFile.setId(10L);
            return storedFile;
        });

        fileService.mkdir(FileServiceTestSupport.workspaceUser(user), "/docs");

        ArgumentCaptor<StoredFile> storedFileCaptor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileRepository).save(storedFileCaptor.capture());
        assertThat(storedFileCaptor.getValue().getLegacyStorageName()).isEqualTo("docs");
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
