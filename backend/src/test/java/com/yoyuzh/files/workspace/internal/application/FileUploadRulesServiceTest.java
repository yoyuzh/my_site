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
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileUploadRulesServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private StoragePolicyQuery storagePolicyQuery;
    @Mock
    private UploadConstraintPolicy uploadConstraintPolicy;

    @Mock
    private FileContentStorage fileContentStorage;

    @Test
    void shouldRejectUploadWhenExceedingEffectiveMaxSize() {
        RuntimeWorkspacePathPolicy workspacePathPolicy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        FileUploadRulesService service = new FileUploadRulesService(
                storedFileRepository,
                storagePolicyQuery,
                uploadConstraintPolicy,
                new WorkspaceNodeRulesService(workspacePathPolicy, workspacePathPolicy),
                2_000L
        );
        User user = createUser(7L, 5_000L, 1_500L);
        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true, true, true, true, false, true, true, false, 900L
        );
        when(storagePolicyQuery.readDefaultPolicySnapshot()).thenReturn(new DefaultStoragePolicySnapshot(
                42L,
                1_200L,
                capabilities
        ));
        when(uploadConstraintPolicy.resolveEffectiveMaxUploadSize(2_000L, 1_500L, 1_200L, 900L)).thenReturn(900L);

        assertThatThrownBy(() -> service.validateUpload(FileServiceTestSupport.workspaceUser(user), "/docs", "a.txt", 901L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectWhenStorageQuotaExceeded() {
        RuntimeWorkspacePathPolicy workspacePathPolicy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        FileUploadRulesService service = new FileUploadRulesService(
                storedFileRepository,
                null,
                null,
                new WorkspaceNodeRulesService(workspacePathPolicy, workspacePathPolicy),
                2_000L
        );
        User user = createUser(7L, 1_000L, 2_000L);
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(990L);

        assertThatThrownBy(() -> service.ensureWithinStorageQuota(FileServiceTestSupport.workspaceUser(user), 20L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldValidateUploadWhenWithinLimitsAndNoConflict() {
        RuntimeWorkspacePathPolicy workspacePathPolicy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        FileUploadRulesService service = new FileUploadRulesService(
                storedFileRepository,
                null,
                null,
                new WorkspaceNodeRulesService(workspacePathPolicy, workspacePathPolicy),
                2_000L
        );
        User user = createUser(7L, 10_000L, 2_000L);
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(500L);

        assertThatCode(() -> service.validateUpload(FileServiceTestSupport.workspaceUser(user), "/docs", "a.txt", 200L))
                .doesNotThrowAnyException();
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
