package com.yoyuzh.files.workspace.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileQuery;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileSnapshot;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileView;
import com.yoyuzh.shared.kernel.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspaceAdminGovernanceApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileService fileService;

    private RuntimeWorkspaceAdminGovernanceApi runtimeWorkspaceAdminGovernanceApi;

    @BeforeEach
    void setUp() {
        runtimeWorkspaceAdminGovernanceApi = new RuntimeWorkspaceAdminGovernanceApi(storedFileRepository, fileService);
    }

    @Test
    void shouldDeleteFileAsAdminAndReturnSnapshot() {
        User owner = createUser(1L, "alice", "alice@example.com");
        StoredFile storedFile = createFile(10L, owner, "/docs", "report.pdf");
        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(storedFile));

        Optional<WorkspaceAdminFileSnapshot> result = runtimeWorkspaceAdminGovernanceApi.deleteFileAsAdmin(10L);

        assertThat(result).isPresent();
        assertThat(result.get().fileId()).isEqualTo(10L);
        assertThat(result.get().ownerUserId()).isEqualTo(1L);
        assertThat(result.get().path()).isEqualTo("/docs");
        assertThat(result.get().filename()).isEqualTo("report.pdf");
        verify(fileService).delete(owner, 10L);
    }

    @Test
    void shouldListFilesAsAdmin() {
        User owner = createUser(1L, "alice", "alice@example.com");
        StoredFile storedFile = createFile(10L, owner, "/docs", "report.pdf");
        when(storedFileRepository.searchAdminFiles(anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(storedFile)));

        PageResponse<WorkspaceAdminFileView> result = runtimeWorkspaceAdminGovernanceApi.listFilesAsAdmin(
                new WorkspaceAdminFileQuery(0, 10, "report", "alice")
        );

        assertThat(result.items()).hasSize(1);
        WorkspaceAdminFileView item = result.items().get(0);
        assertThat(item.fileId()).isEqualTo(10L);
        assertThat(item.filename()).isEqualTo("report.pdf");
        assertThat(item.ownerUsername()).isEqualTo("alice");
        assertThat(item.ownerEmail()).isEqualTo("alice@example.com");
        verify(storedFileRepository).searchAdminFiles(anyString(), anyString(), any());
    }

    @Test
    void shouldReturnEmptyWhenDeletingMissingFileAsAdmin() {
        when(storedFileRepository.findById(anyLong())).thenReturn(Optional.empty());

        Optional<WorkspaceAdminFileSnapshot> result = runtimeWorkspaceAdminGovernanceApi.deleteFileAsAdmin(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldCountFilesAsAdmin() {
        when(storedFileRepository.count()).thenReturn(12L);

        long total = runtimeWorkspaceAdminGovernanceApi.countFilesAsAdmin();

        assertThat(total).isEqualTo(12L);
    }

    private User createUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private StoredFile createFile(Long id, User owner, String path, String filename) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(owner);
        file.setPath(path);
        file.setFilename(filename);
        file.setSize(1024L);
        file.setContentType("application/pdf");
        file.setDirectory(false);
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }
}
