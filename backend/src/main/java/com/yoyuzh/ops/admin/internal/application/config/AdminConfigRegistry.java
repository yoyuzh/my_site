package com.yoyuzh.ops.admin.internal.application.config;

import com.yoyuzh.ops.admin.api.AdminConfigDefinitionResponse;
import com.yoyuzh.ops.admin.api.AdminPermissionCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AdminConfigRegistry {

    private final List<AdminConfigDefinition> definitions = List.of(
            new AdminConfigDefinition(
                    "registration.inviteCodeRequired",
                    "registration",
                    null,
                    "Invite code required",
                    "Whether new registrations must provide the active invite code.",
                    "boolean",
                    defaults -> defaults.registrationInviteCodeRequired(),
                    settings -> settings.registration().inviteCodeRequired(),
                    List.of(),
                    true,
                    true,
                    false,
                    false,
                    Map.of(),
                    AdminPermissionCode.ADMIN_SETTINGS_WRITE.code(),
                    "database"
            ),
            new AdminConfigDefinition(
                    "registration.currentInviteCode",
                    "registration",
                    null,
                    "Current invite code",
                    "The invite code currently used by the registration flow.",
                    "string",
                    defaults -> null,
                    settings -> settings.registration().currentInviteCode(),
                    List.of(),
                    true,
                    false,
                    false,
                    false,
                    Map.of("maxLength", 64),
                    AdminPermissionCode.ADMIN_SETTINGS_READ.code(),
                    "database"
            ),
            new AdminConfigDefinition(
                    "registration.managementRoles",
                    "registration",
                    null,
                    "Registration management roles",
                    "Roles allowed to manage registration controls in the admin panel.",
                    "multi_select",
                    defaults -> defaults.registrationManagementRoles(),
                    settings -> settings.registration().managementRoles(),
                    List.of(
                            new AdminConfigDefinitionResponse.Option("Moderator", "MODERATOR"),
                            new AdminConfigDefinitionResponse.Option("Admin", "ADMIN")
                    ),
                    true,
                    true,
                    false,
                    false,
                    Map.of("minItems", 1, "itemMaxLength", 32),
                    AdminPermissionCode.ADMIN_SETTINGS_WRITE.code(),
                    "database"
            ),
            new AdminConfigDefinition(
                    "transfer.offlineTransferStorageLimitBytes",
                    "transfer",
                    "offlineTransfer",
                    "Offline transfer storage limit",
                    "Maximum bytes reserved for offline transfer storage.",
                    "number",
                    defaults -> null,
                    settings -> settings.transfer().offlineTransferStorageLimitBytes(),
                    List.of(),
                    true,
                    true,
                    false,
                    false,
                    Map.of("min", 1),
                    AdminPermissionCode.ADMIN_SETTINGS_WRITE.code(),
                    "database"
            ),
            new AdminConfigDefinition(
                    "media.metadataExtractionEnabled",
                    "media",
                    "processing",
                    "Metadata extraction",
                    "Whether media metadata extraction is enabled.",
                    "boolean",
                    defaults -> defaults.mediaMetadataExtractionEnabled(),
                    settings -> settings.mediaProcessing().metadataExtractionEnabled(),
                    List.of(),
                    true,
                    false,
                    false,
                    false,
                    Map.of(),
                    AdminPermissionCode.ADMIN_SETTINGS_READ.code(),
                    "runtime"
            ),
            new AdminConfigDefinition(
                    "media.thumbnailGenerationEnabled",
                    "media",
                    "processing",
                    "Thumbnail generation",
                    "Whether thumbnail generation is enabled.",
                    "boolean",
                    defaults -> defaults.mediaThumbnailGenerationEnabled(),
                    settings -> settings.mediaProcessing().thumbnailGenerationEnabled(),
                    List.of(),
                    true,
                    false,
                    false,
                    false,
                    Map.of(),
                    AdminPermissionCode.ADMIN_SETTINGS_READ.code(),
                    "runtime"
            ),
            new AdminConfigDefinition(
                    "media.videoPosterEnabled",
                    "media",
                    "processing",
                    "Video poster generation",
                    "Whether video poster generation is enabled.",
                    "boolean",
                    defaults -> defaults.mediaVideoPosterEnabled(),
                    settings -> settings.mediaProcessing().videoPosterEnabled(),
                    List.of(),
                    true,
                    false,
                    false,
                    false,
                    Map.of(),
                    AdminPermissionCode.ADMIN_SETTINGS_READ.code(),
                    "runtime"
            ),
            new AdminConfigDefinition(
                    "queue.backend",
                    "queue",
                    "mediaMetadata",
                    "Queue backend",
                    "Backend used for media metadata scheduling.",
                    "string",
                    defaults -> defaults.queueBackend(),
                    settings -> settings.queue().backend(),
                    List.of(
                            new AdminConfigDefinitionResponse.Option("In-memory", "in-memory"),
                            new AdminConfigDefinitionResponse.Option("Redis", "redis")
                    ),
                    true,
                    false,
                    false,
                    false,
                    Map.of("maxLength", 32),
                    AdminPermissionCode.ADMIN_SETTINGS_READ.code(),
                    "environment"
            ),
            new AdminConfigDefinition(
                    "queue.mediaMetadataFixedDelayMs",
                    "queue",
                    "mediaMetadata",
                    "Metadata fixed delay",
                    "Fixed delay in milliseconds between metadata queue runs.",
                    "number",
                    defaults -> defaults.queueMediaMetadataFixedDelayMs(),
                    settings -> settings.queue().mediaMetadataFixedDelayMs(),
                    List.of(),
                    true,
                    false,
                    false,
                    false,
                    Map.of("min", 1),
                    AdminPermissionCode.ADMIN_SETTINGS_READ.code(),
                    "environment"
            ),
            new AdminConfigDefinition(
                    "queue.mediaMetadataInitialDelayMs",
                    "queue",
                    "mediaMetadata",
                    "Metadata initial delay",
                    "Initial delay in milliseconds before metadata queue startup.",
                    "number",
                    defaults -> defaults.queueMediaMetadataInitialDelayMs(),
                    settings -> settings.queue().mediaMetadataInitialDelayMs(),
                    List.of(),
                    true,
                    false,
                    false,
                    false,
                    Map.of("min", 1),
                    AdminPermissionCode.ADMIN_SETTINGS_READ.code(),
                    "environment"
            ),
            new AdminConfigDefinition(
                    "server.storageProvider",
                    "server",
                    null,
                    "Storage provider",
                    "Active storage provider used by the server runtime.",
                    "string",
                    defaults -> defaults.serverStorageProvider(),
                    settings -> settings.server().storageProvider(),
                    List.of(
                            new AdminConfigDefinitionResponse.Option("Local", "local"),
                            new AdminConfigDefinitionResponse.Option("S3 compatible", "s3")
                    ),
                    true,
                    false,
                    false,
                    false,
                    Map.of("maxLength", 32),
                    AdminPermissionCode.ADMIN_SETTINGS_READ.code(),
                    "environment"
            ),
            new AdminConfigDefinition(
                    "server.redisEnabled",
                    "server",
                    null,
                    "Redis enabled",
                    "Whether Redis-backed capabilities are enabled for the server runtime.",
                    "boolean",
                    defaults -> defaults.serverRedisEnabled(),
                    settings -> settings.server().redisEnabled(),
                    List.of(),
                    true,
                    true,
                    false,
                    false,
                    Map.of(),
                    AdminPermissionCode.ADMIN_SETTINGS_WRITE.code(),
                    "database"
            )
    );

    public List<AdminConfigDefinition> definitions() {
        return definitions;
    }
}
