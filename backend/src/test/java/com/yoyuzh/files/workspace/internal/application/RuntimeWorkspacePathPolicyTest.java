package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class RuntimeWorkspacePathPolicyTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Test
    void shouldNormalizeDirectoryPath() {
        RuntimeWorkspacePathPolicy policy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);

        assertThat(policy.normalizeDirectoryPath("docs//images/")).isEqualTo("/docs/images");
    }

    @Test
    void shouldCreateMissingDirectoryHierarchy() {
        RuntimeWorkspacePathPolicy policy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        when(storedFileRepository.findByUserIdAndPathAndFilename(eq(7L), any(), any())).thenReturn(Optional.empty());
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        policy.ensureDirectoryHierarchy(7L, "/projects/site");

        verify(fileContentStorage).ensureDirectory(7L, "/projects");
        verify(fileContentStorage).ensureDirectory(7L, "/projects/site");
        verify(storedFileRepository, times(2)).save(any(StoredFile.class));
    }

    @Test
    void shouldRejectRecycleRestoreWhenTargetAlreadyExists() {
        RuntimeWorkspacePathPolicy policy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        StoredFile recycledFile = new StoredFile();
        recycledFile.setFilename("notes.txt");
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(true);

        assertThatThrownBy(() -> policy.validateRecycleRestoreTargets(
                7L,
                List.of(recycledFile),
                ignored -> "/docs"
        )).isInstanceOf(BusinessException.class);
    }

}
