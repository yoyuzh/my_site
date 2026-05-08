package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.content.api.FileContentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(storedFileRepository.findActiveNodesByUserIdAndPathInAndFilenameIn(eq(7L), any(), any())).thenReturn(List.of());
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

    @Test
    void shouldResolveAvailableFileNameByAppendingCounterBeforeExtension() {
        RuntimeWorkspacePathPolicy policy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        when(storedFileRepository.findActiveFilenamesByUserIdAndPathAndFilenamePrefix(7L, "/docs", "report.txt", "report"))
                .thenReturn(List.of("report.txt", "report(1).txt", "report(2).md", "report-final.txt"));

        assertThat(policy.resolveAvailableNodeName(7L, "/docs", "report.txt")).isEqualTo("report(2).txt");
    }

    @Test
    void shouldResolveAvailableDirectoryNameByAppendingCounter() {
        RuntimeWorkspacePathPolicy policy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        when(storedFileRepository.findActiveFilenamesByUserIdAndPathAndFilenamePrefix(7L, "/", "docs", "docs"))
                .thenReturn(List.of("docs", "docs(1).txt"));

        assertThat(policy.resolveAvailableNodeName(7L, "/", "docs")).isEqualTo("docs(1)");
    }

    @Test
    void shouldFailWhenAutoResolvedNamesExceedRetryLimit() {
        RuntimeWorkspacePathPolicy policy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        java.util.ArrayList<String> existingNames = new java.util.ArrayList<>();
        existingNames.add("report.txt");
        for (int counter = 1; counter <= 100; counter++) {
            existingNames.add("report(" + counter + ").txt");
        }
        when(storedFileRepository.findActiveFilenamesByUserIdAndPathAndFilenamePrefix(7L, "/docs", "report.txt", "report"))
                .thenReturn(existingNames);

        assertThatThrownBy(() -> policy.resolveAvailableNodeName(7L, "/docs", "report.txt"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NAME);
    }

}
