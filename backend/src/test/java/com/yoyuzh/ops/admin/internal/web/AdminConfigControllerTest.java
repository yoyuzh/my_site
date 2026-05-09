package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.boot.web.GlobalExceptionHandler;
import com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsDefaults;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsService;
import com.yoyuzh.ops.admin.internal.application.config.AdminConfigRegistry;
import com.yoyuzh.ops.admin.internal.application.config.RuntimeAdminConfigSchemaApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminConfigControllerTest {

    @Mock
    private AdminConfigSnapshotService adminConfigSnapshotService;
    @Mock
    private AdminRuntimeSettingsDefaults adminRuntimeSettingsDefaults;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RuntimeAdminConfigSchemaApi adminConfigSchemaApi = new RuntimeAdminConfigSchemaApi(
                new AdminConfigRegistry(),
                adminConfigSnapshotService,
                adminRuntimeSettingsDefaults
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminConfigController(adminConfigSchemaApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void definitionsEndpointShouldReturnRegistrationAndServerKeys() throws Exception {
        when(adminConfigSnapshotService.getSettings()).thenReturn(settingsResponse(
                false,
                "INV-2026",
                List.of("ADMIN", "MODERATOR"),
                5368709120L,
                true,
                false,
                true,
                "redis",
                3000L,
                15000L,
                "s3",
                true
        ));
        when(adminRuntimeSettingsDefaults.create()).thenReturn(new AdminRuntimeSettingsService.State(
                false,
                true,
                List.of("MODERATOR", "ADMIN"),
                1800L,
                604800L,
                true,
                120L,
                true,
                false,
                false,
                "redis",
                3000L,
                15000L,
                false,
                "local",
                true
        ));

        mockMvc.perform(get("/api/admin/config/definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].key").value("registration.inviteCodeRequired"))
                .andExpect(jsonPath("$.data[1].key").value("registration.currentInviteCode"))
                .andExpect(jsonPath("$.data[10].key").value("server.storageProvider"))
                .andExpect(jsonPath("$.data[11].key").value("server.redisEnabled"));
    }

    @Test
    void snapshotEndpointShouldReturnCurrentValuesFromSnapshotService() throws Exception {
        when(adminConfigSnapshotService.getSettings()).thenReturn(settingsResponse(
                false,
                "INV-CURRENT",
                List.of("ADMIN"),
                8589934592L,
                false,
                true,
                false,
                "in-memory",
                5000L,
                25000L,
                "local",
                false
        ));
        when(adminRuntimeSettingsDefaults.create()).thenReturn(new AdminRuntimeSettingsService.State(
                false,
                true,
                List.of("MODERATOR", "ADMIN"),
                1800L,
                604800L,
                true,
                120L,
                true,
                false,
                false,
                "redis",
                3000L,
                15000L,
                false,
                "s3",
                true
        ));

        mockMvc.perform(get("/api/admin/config/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fields").isArray())
                .andExpect(jsonPath("$.data.fields[0].value").value(false))
                .andExpect(jsonPath("$.data.fields[1].value").value("INV-CURRENT"))
                .andExpect(jsonPath("$.data.fields[2].value[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.fields[3].value").value(8589934592L))
                .andExpect(jsonPath("$.data.fields[7].value").value("in-memory"))
                .andExpect(jsonPath("$.data.fields[10].value").value("local"))
                .andExpect(jsonPath("$.data.fields[11].value").value(false));
    }

    private AdminSettingsResponse settingsResponse(boolean inviteCodeRequired,
                                                   String currentInviteCode,
                                                   List<String> managementRoles,
                                                   long offlineTransferStorageLimitBytes,
                                                   boolean metadataExtractionEnabled,
                                                   boolean thumbnailGenerationEnabled,
                                                   boolean videoPosterEnabled,
                                                   String queueBackend,
                                                   long queueMediaMetadataFixedDelayMs,
                                                   long queueMediaMetadataInitialDelayMs,
                                                   String storageProvider,
                                                   boolean redisEnabled) {
        return new AdminSettingsResponse(
                new AdminSettingsResponse.SiteSection(false, false),
                new AdminSettingsResponse.RegistrationSection(
                        inviteCodeRequired,
                        currentInviteCode,
                        managementRoles,
                        true
                ),
                new AdminSettingsResponse.UserSessionSection(1800L, 604800L, true, 120L, false),
                new AdminSettingsResponse.TransferSection(offlineTransferStorageLimitBytes, true),
                new AdminSettingsResponse.MediaProcessingSection(
                        metadataExtractionEnabled,
                        thumbnailGenerationEnabled,
                        videoPosterEnabled,
                        false
                ),
                new AdminSettingsResponse.QueueSection(
                        queueBackend,
                        queueMediaMetadataFixedDelayMs,
                        queueMediaMetadataInitialDelayMs,
                        false
                ),
                new AdminSettingsResponse.AppearanceSection(false, false),
                new AdminSettingsResponse.ServerSection(storageProvider, redisEnabled, false)
        );
    }
}
