package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.boot.web.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Mock
    private com.yoyuzh.ops.admin.internal.application.config.AdminConfigValueGovernanceService adminConfigValueGovernanceService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RuntimeAdminConfigSchemaApi adminConfigSchemaApi = new RuntimeAdminConfigSchemaApi(
                new AdminConfigRegistry(),
                adminConfigSnapshotService,
                adminRuntimeSettingsDefaults,
                adminConfigValueGovernanceService
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

        MvcResult result = mockMvc.perform(get("/api/admin/config/definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        Map<String, JsonNode> definitionsByKey = responseItemsByKey(result, "data");

        assertThat(definitionsByKey).containsKeys(
                "registration.inviteCodeRequired",
                "registration.currentInviteCode",
                "registration.managementRoles",
                "transfer.offlineTransferStorageLimitBytes",
                "queue.mediaMetadataFixedDelayMs",
                "queue.mediaMetadataInitialDelayMs",
                "server.storageProvider",
                "server.redisEnabled"
        );
        assertThat(definitionsByKey.get("registration.currentInviteCode").path("editable").asBoolean()).isFalse();
        assertThat(definitionsByKey.get("registration.currentInviteCode").path("permissionCode").asText())
                .isEqualTo("admin.settings.read");
        assertThat(definitionsByKey.get("registration.managementRoles").path("type").asText()).isEqualTo("multi_select");
        assertThat(definitionsByKey.get("transfer.offlineTransferStorageLimitBytes").path("type").asText()).isEqualTo("number");
        assertThat(definitionsByKey.get("queue.mediaMetadataFixedDelayMs").path("type").asText()).isEqualTo("number");
        assertThat(definitionsByKey.get("queue.mediaMetadataInitialDelayMs").path("type").asText()).isEqualTo("number");
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

        MvcResult result = mockMvc.perform(get("/api/admin/config/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fields").isArray())
                .andReturn();

        Map<String, JsonNode> fieldsByKey = responseItemsByKey(result, "data.fields");

        assertThat(fieldsByKey).containsKeys(
                "registration.inviteCodeRequired",
                "registration.currentInviteCode",
                "registration.managementRoles",
                "transfer.offlineTransferStorageLimitBytes",
                "queue.backend",
                "server.storageProvider",
                "server.redisEnabled"
        );
        assertThat(fieldsByKey.get("registration.inviteCodeRequired").path("value").asBoolean()).isFalse();
        assertThat(fieldsByKey.get("registration.currentInviteCode").path("value").asText()).isEqualTo("INV-CURRENT");
        assertThat(fieldsByKey.get("registration.managementRoles").path("value").isArray()).isTrue();
        assertThat(fieldsByKey.get("registration.managementRoles").path("value")).hasSize(1);
        assertThat(fieldsByKey.get("registration.managementRoles").path("value").get(0).asText()).isEqualTo("ADMIN");
        assertThat(fieldsByKey.get("transfer.offlineTransferStorageLimitBytes").path("value").asLong())
                .isEqualTo(8589934592L);
        assertThat(fieldsByKey.get("queue.backend").path("value").asText()).isEqualTo("in-memory");
        assertThat(fieldsByKey.get("server.storageProvider").path("value").asText()).isEqualTo("local");
        assertThat(fieldsByKey.get("server.redisEnabled").path("value").asBoolean()).isFalse();
    }

    private Map<String, JsonNode> responseItemsByKey(MvcResult result, String dataPath) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode items = root.at(pointer(dataPath));
        Map<String, JsonNode> itemsByKey = new HashMap<>();
        for (JsonNode item : items) {
            itemsByKey.put(item.path("key").asText(), item);
        }
        return itemsByKey;
    }

    private String pointer(String dataPath) {
        return "/" + dataPath.replace(".", "/");
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
                new AdminSettingsResponse.ServerSection(storageProvider, redisEnabled, true)
        );
    }
}
