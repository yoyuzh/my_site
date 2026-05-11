package com.yoyuzh.ops.admin.internal.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.ops.admin.api.AdminConfigDefinitionResponse;
import com.yoyuzh.ops.admin.api.AdminConfigUpdateRequest;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.ops.admin.internal.application.AdminAuditAction;
import com.yoyuzh.ops.admin.internal.application.AdminAuditService;
import com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService;
import com.yoyuzh.ops.admin.internal.application.AdminMutableSettingsService;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsDefaults;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsService;
import com.yoyuzh.ops.admin.internal.infra.config.AdminConfigChangeLogEntity;
import com.yoyuzh.ops.admin.internal.infra.config.AdminConfigChangeLogRepository;
import com.yoyuzh.ops.admin.internal.infra.config.AdminConfigValueEntity;
import com.yoyuzh.ops.admin.internal.infra.config.AdminConfigValueRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConfigValueGovernanceServiceTest {

    @Mock
    private AdminConfigSnapshotService adminConfigSnapshotService;
    @Mock
    private AdminRuntimeSettingsDefaults adminRuntimeSettingsDefaults;
    @Mock
    private AdminMutableSettingsService adminMutableSettingsService;
    @Mock
    private AdminConfigValueRepository adminConfigValueRepository;
    @Mock
    private AdminConfigChangeLogRepository adminConfigChangeLogRepository;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminConfigValueGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new AdminConfigValueGovernanceService(
                new AdminConfigRegistry(),
                adminConfigSnapshotService,
                adminRuntimeSettingsDefaults,
                adminMutableSettingsService,
                adminConfigValueRepository,
                adminConfigChangeLogRepository,
                adminAuditService,
                new ObjectMapper()
        );
    }

    @Test
    void shouldUpdateEditableRegistrationConfigThroughRealSettingsService() {
        when(adminConfigSnapshotService.getSettings())
                .thenReturn(settings(false, List.of("ADMIN"), 1024L))
                .thenReturn(settings(true, List.of("ADMIN"), 1024L))
                .thenReturn(settings(true, List.of("ADMIN"), 1024L));
        when(adminRuntimeSettingsDefaults.create()).thenReturn(defaults());
        when(adminConfigValueRepository.findByConfigKeyForUpdate("registration.inviteCodeRequired"))
                .thenReturn(Optional.empty());
        when(adminConfigValueRepository.save(any(AdminConfigValueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminConfigDefinitionResponse response = service.updateValue(
                "registration.inviteCodeRequired",
                new AdminConfigUpdateRequest(true, "enable invite gate")
        );

        assertThat(response.key()).isEqualTo("registration.inviteCodeRequired");
        assertThat(response.value()).isEqualTo(true);
        verify(adminMutableSettingsService).updateSettings(argThat(request ->
                request.registration() != null
                        && request.registration().inviteCodeRequired()
                        && request.registration().managementRoles().equals(List.of("ADMIN"))
        ));
        ArgumentCaptor<AdminConfigChangeLogEntity> changeCaptor = ArgumentCaptor.forClass(AdminConfigChangeLogEntity.class);
        verify(adminConfigChangeLogRepository).save(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getBeforeValueJson()).isEqualTo("false");
        assertThat(changeCaptor.getValue().getAfterValueJson()).isEqualTo("true");
        assertThat(changeCaptor.getValue().getVersion()).isEqualTo(1L);
        verify(adminAuditService).record(
                eq(AdminAuditAction.CONFIG_VALUE_UPDATED),
                eq("ADMIN_CONFIG"),
                eq(null),
                eq("Updated admin config value"),
                argThat(details -> details.get("key").equals("registration.inviteCodeRequired"))
        );
    }

    @Test
    void shouldRejectEnvironmentConfigUpdate() {
        assertThatThrownBy(() -> service.updateValue(
                "server.storageProvider",
                new AdminConfigUpdateRequest("s3", "try update environment")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("read-only");
        verify(adminMutableSettingsService, never()).updateSettings(any());
    }

    @Test
    void shouldUpdateRedisConfigThroughRealSettingsService() {
        when(adminConfigSnapshotService.getSettings())
                .thenReturn(settings(false, List.of("ADMIN"), 1024L, false))
                .thenReturn(settings(false, List.of("ADMIN"), 1024L, true))
                .thenReturn(settings(false, List.of("ADMIN"), 1024L, true));
        when(adminRuntimeSettingsDefaults.create()).thenReturn(defaults());
        when(adminConfigValueRepository.findByConfigKeyForUpdate("server.redisEnabled"))
                .thenReturn(Optional.empty());
        when(adminConfigValueRepository.save(any(AdminConfigValueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminConfigDefinitionResponse response = service.updateValue(
                "server.redisEnabled",
                new AdminConfigUpdateRequest(true, "enable redis")
        );

        assertThat(response.key()).isEqualTo("server.redisEnabled");
        assertThat(response.value()).isEqualTo(true);
        assertThat(response.editable()).isTrue();
        assertThat(response.source()).isEqualTo("database");
        verify(adminMutableSettingsService).updateSettings(argThat(request ->
                request.server() != null
                        && request.server().redisEnabled()
                        && request.server().storageProvider().equals("local")
        ));
        ArgumentCaptor<AdminConfigChangeLogEntity> changeCaptor = ArgumentCaptor.forClass(AdminConfigChangeLogEntity.class);
        verify(adminConfigChangeLogRepository).save(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getBeforeValueJson()).isEqualTo("false");
        assertThat(changeCaptor.getValue().getAfterValueJson()).isEqualTo("true");
    }

    @Test
    void shouldRejectUnknownConfigKey() {
        assertThatThrownBy(() -> service.updateValue(
                "missing.key",
                new AdminConfigUpdateRequest(true, "unknown")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unknown config key");
    }

    @Test
    void shouldIgnoreNoopUpdateWithoutWritingHistory() {
        when(adminConfigSnapshotService.getSettings())
                .thenReturn(settings(false, List.of("ADMIN"), 1024L))
                .thenReturn(settings(false, List.of("ADMIN"), 1024L));
        when(adminRuntimeSettingsDefaults.create()).thenReturn(defaults());

        AdminConfigDefinitionResponse response = service.updateValue(
                "registration.inviteCodeRequired",
                new AdminConfigUpdateRequest(false, "no-op")
        );

        assertThat(response.value()).isEqualTo(false);
        verify(adminMutableSettingsService, never()).updateSettings(any());
        verify(adminConfigChangeLogRepository, never()).save(any());
        verify(adminAuditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnConfigHistoryPage() {
        AdminConfigChangeLogEntity changeLog = new AdminConfigChangeLogEntity();
        changeLog.setConfigKey("registration.managementRoles");
        changeLog.setBeforeValueJson("[\"ADMIN\"]");
        changeLog.setAfterValueJson("[\"MODERATOR\",\"ADMIN\"]");
        changeLog.setVersion(2L);
        changeLog.setReason("broaden access");
        changeLog.setActorUsername("service-admin");
        when(adminConfigChangeLogRepository.findByConfigKeyOrderByVersionDesc(
                eq("registration.managementRoles"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(changeLog)));

        PageResponse<com.yoyuzh.ops.admin.api.AdminConfigHistoryResponse> response =
                service.history("registration.managementRoles", 0, 10);

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items().get(0).afterValue()).isEqualTo(List.of("MODERATOR", "ADMIN"));
    }

    @Test
    void shouldRollbackToTargetVersionAfterValue() {
        AdminConfigChangeLogEntity target = new AdminConfigChangeLogEntity();
        target.setConfigKey("registration.managementRoles");
        target.setBeforeValueJson("[\"ADMIN\"]");
        target.setAfterValueJson("[\"MODERATOR\",\"ADMIN\"]");
        target.setVersion(2L);
        target.setReason("target");
        target.setActorUsername("service-admin");
        when(adminConfigChangeLogRepository.findFirstByConfigKeyAndVersion("registration.managementRoles", 2L))
                .thenReturn(Optional.of(target));
        when(adminConfigSnapshotService.getSettings())
                .thenReturn(settings(true, List.of("ADMIN"), 1024L))
                .thenReturn(settings(true, List.of("MODERATOR", "ADMIN"), 1024L))
                .thenReturn(settings(true, List.of("MODERATOR", "ADMIN"), 1024L));
        when(adminRuntimeSettingsDefaults.create()).thenReturn(defaults());
        AdminConfigValueEntity current = new AdminConfigValueEntity();
        current.setConfigKey("registration.managementRoles");
        current.setVersion(2L);
        when(adminConfigValueRepository.findByConfigKeyForUpdate("registration.managementRoles"))
                .thenReturn(Optional.of(current));
        when(adminConfigValueRepository.save(any(AdminConfigValueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminConfigDefinitionResponse response = service.rollback("registration.managementRoles", 2L);

        assertThat(response.value()).isEqualTo(List.of("MODERATOR", "ADMIN"));
        verify(adminMutableSettingsService).updateSettings(argThat(request ->
                request.registration() != null
                        && request.registration().managementRoles().equals(List.of("MODERATOR", "ADMIN"))
        ));
        verify(adminAuditService).record(
                eq(AdminAuditAction.CONFIG_VALUE_ROLLED_BACK),
                eq("ADMIN_CONFIG"),
                eq(null),
                eq("Rolled back admin config value"),
                argThat(details -> details.get("key").equals("registration.managementRoles")
                        && Long.valueOf(3L).equals(details.get("version")))
        );
    }

    private AdminRuntimeSettingsService.State defaults() {
        return new AdminRuntimeSettingsService.State(
                false,
                false,
                List.of("ADMIN"),
                1800L,
                604800L,
                true,
                120L,
                true,
                false,
                false,
                "in-memory",
                3000L,
                15000L,
                false,
                "local",
                false
        );
    }

    private AdminSettingsResponse settings(boolean inviteRequired, List<String> managementRoles, long offlineLimit) {
        return settings(inviteRequired, managementRoles, offlineLimit, false);
    }

    private AdminSettingsResponse settings(boolean inviteRequired,
                                           List<String> managementRoles,
                                           long offlineLimit,
                                           boolean redisEnabled) {
        return new AdminSettingsResponse(
                new AdminSettingsResponse.SiteSection(false, false),
                new AdminSettingsResponse.RegistrationSection(
                        inviteRequired,
                        "INV-CURRENT",
                        managementRoles,
                        true
                ),
                new AdminSettingsResponse.UserSessionSection(1800L, 604800L, true, 120L, false),
                new AdminSettingsResponse.TransferSection(offlineLimit, true),
                new AdminSettingsResponse.MediaProcessingSection(true, false, false, false),
                new AdminSettingsResponse.QueueSection("in-memory", 3000L, 15000L, false),
                new AdminSettingsResponse.AppearanceSection(false, false),
                new AdminSettingsResponse.ServerSection("local", redisEnabled, true)
        );
    }
}
