package com.yoyuzh.files.core;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalImportRulesServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Test
    void shouldNormalizeDirectoriesAndFiles() {
        ExternalImportRulesService service = createService(10_000L);

        List<String> directories = service.normalizeDirectories(List.of("docs//a/", "/docs", "docs/a"));
        List<FileService.ExternalFileImport> files = service.normalizeFiles(List.of(
                new FileService.ExternalFileImport("docs/a", "x.txt", null, null)
        ));

        assertThat(directories).containsExactly("/docs", "/docs/a");
        assertThat(files).hasSize(1);
        assertThat(files.get(0).path()).isEqualTo("/docs/a");
        assertThat(files.get(0).filename()).isEqualTo("x.txt");
        assertThat(files.get(0).contentType()).isEqualTo("application/octet-stream");
        assertThat(files.get(0).content()).isEmpty();
    }

    @Test
    void shouldRejectDuplicatePlannedTargetsInsideBatch() {
        ExternalImportRulesService service = createService(10_000L);
        User user = createUser(7L, 10_000L, 10_000L);
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(0L);

        assertThatThrownBy(() -> service.validateBatch(
                user,
                List.of("/docs/a"),
                List.of(new FileService.ExternalFileImport("/docs", "a", "text/plain", new byte[]{1}))
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldAcceptBatchWhenNoQuotaAndTargetConflicts() {
        ExternalImportRulesService service = createService(10_000L);
        User user = createUser(7L, 10_000L, 10_000L);
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(100L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/", "docs")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "a.txt")).thenReturn(false);

        assertThatCode(() -> service.validateBatch(
                user,
                List.of("/docs"),
                List.of(new FileService.ExternalFileImport("/docs", "a.txt", "text/plain", new byte[]{1, 2, 3}))
        )).doesNotThrowAnyException();
    }

    private ExternalImportRulesService createService(long maxFileSize) {
        WorkspaceNodeRulesService workspaceNodeRulesService = new WorkspaceNodeRulesService(storedFileRepository, fileContentStorage);
        FileUploadRulesService fileUploadRulesService = new FileUploadRulesService(
                storedFileRepository,
                null,
                workspaceNodeRulesService,
                maxFileSize
        );
        return new ExternalImportRulesService(workspaceNodeRulesService, fileUploadRulesService);
    }

    private User createUser(Long id, Long storageQuotaBytes, Long maxUploadSizeBytes) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.com");
        user.setPasswordHash("encoded");
        user.setCreatedAt(LocalDateTime.now());
        user.setStorageQuotaBytes(storageQuotaBytes);
        user.setMaxUploadSizeBytes(maxUploadSizeBytes);
        return user;
    }
}
