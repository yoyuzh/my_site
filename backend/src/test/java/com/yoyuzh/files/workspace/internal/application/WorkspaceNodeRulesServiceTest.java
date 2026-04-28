package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.internal.application.*;
import com.yoyuzh.files.workspace.internal.domain.*;
import com.yoyuzh.files.workspace.internal.infra.*;
import com.yoyuzh.files.workspace.internal.web.*;
import com.yoyuzh.files.content.internal.application.*;
import com.yoyuzh.files.content.internal.domain.*;
import com.yoyuzh.files.content.internal.infra.*;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.content.api.FileContentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceNodeRulesServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Test
    void shouldNormalizeDirectoryPath() {
        WorkspaceNodeRulesService rulesService = createRulesService();

        String normalized = rulesService.normalizeDirectoryPath("docs//images/");

        assertThat(normalized).isEqualTo("/docs/images");
    }

    @Test
    void shouldRejectPathTraversalDirectoryPath() {
        WorkspaceNodeRulesService rulesService = createRulesService();

        assertThatThrownBy(() -> rulesService.normalizeDirectoryPath("../docs"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldCreateMissingDirectoryHierarchy() {
        WorkspaceNodeRulesService rulesService = createRulesService();
        User user = createUser(7L);
        when(storedFileRepository.findByUserIdAndPathAndFilename(eq(7L), any(), any())).thenReturn(Optional.empty());
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rulesService.ensureDirectoryHierarchy(user.getId(), "/projects/site");

        verify(fileContentStorage).ensureDirectory(7L, "/projects");
        verify(fileContentStorage).ensureDirectory(7L, "/projects/site");
        verify(storedFileRepository, times(2)).save(any(StoredFile.class));
    }

    @Test
    void shouldRejectExistingPathWhenEntryIsFile() {
        WorkspaceNodeRulesService rulesService = createRulesService();
        StoredFile file = createFile(11L, 7L, "/", "projects");
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "projects"))
                .thenReturn(Optional.of(file));

        assertThatThrownBy(() -> rulesService.ensureExistingDirectoryPath(7L, "/projects"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectUnavailableNodeName() {
        WorkspaceNodeRulesService rulesService = createRulesService();
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(true);

        assertThatThrownBy(() -> rulesService.ensureNodeNameAvailable(7L, "/docs", "notes.txt", "冲突"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("冲突");
    }

    @Test
    void shouldRejectRecycleRestoreWhenTargetAlreadyExists() {
        WorkspaceNodeRulesService rulesService = createRulesService();
        StoredFile recycledFile = new StoredFile();
        recycledFile.setFilename("notes.txt");
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(true);

        assertThatThrownBy(() -> rulesService.validateRecycleRestoreTargets(
                7L,
                List.of(recycledFile),
                ignored -> "/docs"
        )).isInstanceOf(BusinessException.class);
    }

    private WorkspaceNodeRulesService createRulesService() {
        RuntimeWorkspacePathPolicy workspacePathPolicy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        return new WorkspaceNodeRulesService(workspacePathPolicy, workspacePathPolicy);
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

    private StoredFile createFile(Long id, Long userId, String path, String filename) {
        User user = createUser(userId);
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUserId(user.getId());
        file.setFilename(filename);
        file.setPath(path);
        file.setSize(5L);
        file.setDirectory(false);
        file.setContentType("text/plain");
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }
}
