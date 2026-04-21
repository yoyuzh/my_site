package com.yoyuzh.files.upload.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.upload.api.UploadCompletionCommand;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuntimeUploadCompletionApiTest {

    @Mock
    private WorkspacePathPolicy workspacePathPolicy;
    @Mock
    private ContentRegistrationApi contentRegistrationApi;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private FileContentStorage fileContentStorage;

    private RuntimeUploadCompletionApi uploadCompletionApi;

    @BeforeEach
    void setUp() {
        uploadCompletionApi = new RuntimeUploadCompletionApi(
                workspacePathPolicy,
                contentRegistrationApi,
                fileBlobRepository,
                fileContentStorage
        );
    }

    @Test
    void shouldEnsureDirectoryAndRegisterStoredBlobThroughContentApi() {
        User user = createUser(7L);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> {
            FileBlob blob = invocation.getArgument(0);
            blob.setId(100L);
            return blob;
        });
        when(contentRegistrationApi.registerBlob(any(ContentRegistrationCommand.class))).thenReturn(
                new RegisteredContentFile(10L, "movie.mp4", "/docs", 20L, "video/mp4", false, LocalDateTime.now())
        );

        RegisteredContentFile result = uploadCompletionApi.completeStoredBlob(new UploadCompletionCommand(
                user.getId(),
                "/docs",
                "movie.mp4",
                "blobs/session-1",
                "video/mp4",
                20L
        ));

        assertThat(result.filename()).isEqualTo("movie.mp4");
        verify(workspacePathPolicy).ensureDirectoryHierarchy(7L, "/docs");
        ArgumentCaptor<ContentRegistrationCommand> commandCaptor = ArgumentCaptor.forClass(ContentRegistrationCommand.class);
        verify(contentRegistrationApi).registerBlob(commandCaptor.capture());
        assertThat(commandCaptor.getValue().normalizedPath()).isEqualTo("/docs");
        assertThat(commandCaptor.getValue().filename()).isEqualTo("movie.mp4");
        assertThat(commandCaptor.getValue().userId()).isEqualTo(7L);
        ContentBlobReference blob = commandCaptor.getValue().blob();
        assertThat(blob.objectKey()).isEqualTo("blobs/session-1");
    }

    @Test
    void shouldDeleteStoredBlobWhenRegistrationFails() {
        User user = createUser(7L);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentRegistrationApi.registerBlob(any(ContentRegistrationCommand.class)))
                .thenThrow(new IllegalStateException("registration failed"));

        assertThatThrownBy(() -> uploadCompletionApi.completeStoredBlob(new UploadCompletionCommand(
                user.getId(),
                "/docs",
                "movie.mp4",
                "blobs/session-1",
                "video/mp4",
                20L
        ))).isInstanceOf(IllegalStateException.class);

        verify(fileContentStorage).deleteBlob(eq("blobs/session-1"));
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
