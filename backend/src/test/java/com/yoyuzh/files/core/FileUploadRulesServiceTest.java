package com.yoyuzh.files.core;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileUploadRulesServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private StoragePolicyService storagePolicyService;

    @Mock
    private FileContentStorage fileContentStorage;

    @Test
    void shouldRejectUploadWhenExceedingEffectiveMaxSize() {
        FileUploadRulesService service = new FileUploadRulesService(
                storedFileRepository,
                storagePolicyService,
                new WorkspaceNodeRulesService(storedFileRepository, fileContentStorage),
                2_000L
        );
        User user = createUser(7L, 5_000L, 1_500L);
        StoragePolicy defaultPolicy = new StoragePolicy();
        defaultPolicy.setMaxSizeBytes(1_200L);
        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true, true, true, true, false, true, true, false, 900L
        );
        when(storagePolicyService.ensureDefaultPolicy()).thenReturn(defaultPolicy);
        when(storagePolicyService.readCapabilities(defaultPolicy)).thenReturn(capabilities);

        assertThatThrownBy(() -> service.validateUpload(user, "/docs", "a.txt", 901L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectWhenStorageQuotaExceeded() {
        FileUploadRulesService service = new FileUploadRulesService(
                storedFileRepository,
                null,
                new WorkspaceNodeRulesService(storedFileRepository, fileContentStorage),
                2_000L
        );
        User user = createUser(7L, 1_000L, 2_000L);
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(990L);

        assertThatThrownBy(() -> service.ensureWithinStorageQuota(user, 20L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldValidateUploadWhenWithinLimitsAndNoConflict() {
        FileUploadRulesService service = new FileUploadRulesService(
                storedFileRepository,
                null,
                new WorkspaceNodeRulesService(storedFileRepository, fileContentStorage),
                2_000L
        );
        User user = createUser(7L, 10_000L, 2_000L);
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(500L);

        assertThatCode(() -> service.validateUpload(user, "/docs", "a.txt", 200L))
                .doesNotThrowAnyException();

        verify(storedFileRepository).existsByUserIdAndPathAndFilename(7L, "/docs", "a.txt");
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
