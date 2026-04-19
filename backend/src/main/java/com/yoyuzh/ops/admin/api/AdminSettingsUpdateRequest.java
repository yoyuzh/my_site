package com.yoyuzh.ops.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminSettingsUpdateRequest(
        @Valid
        SiteSection site,
        @Valid
        RegistrationSection registration,
        @Valid
        UserSessionSection userSession,
        @Valid
        TransferSection transfer,
        @Valid
        MediaProcessingSection mediaProcessing,
        @Valid
        QueueSection queue,
        @Valid
        AppearanceSection appearance,
        @Valid
        ServerSection server
) {

    public boolean hasWritableSections() {
        return registration != null || transfer != null;
    }

    public record SiteSection(
            boolean supported
    ) {
    }

    public record RegistrationSection(
            boolean inviteCodeRequired,
            @Size(max = 64, message = "inviteCode too long")
            String currentInviteCode,
            @NotEmpty(message = "managementRoles cannot be empty")
            List<@NotBlank(message = "management role cannot be blank") @Size(max = 32, message = "management role too long") String> managementRoles
    ) {
    }

    public record UserSessionSection(
            @Positive(message = "accessExpirationSeconds must be positive")
            long accessExpirationSeconds,
            @Positive(message = "refreshExpirationSeconds must be positive")
            long refreshExpirationSeconds,
            boolean tokenBlacklistEnabled,
            @Positive(message = "tokenBlacklistTtlBufferSeconds must be positive")
            long tokenBlacklistTtlBufferSeconds
    ) {
    }

    public record TransferSection(
            @Positive(message = "offlineTransferStorageLimitBytes must be positive")
            long offlineTransferStorageLimitBytes
    ) {
    }

    public record MediaProcessingSection(
            boolean metadataExtractionEnabled,
            boolean thumbnailGenerationEnabled,
            boolean videoPosterEnabled
    ) {
    }

    public record QueueSection(
            @NotBlank(message = "queue backend cannot be blank")
            @Size(max = 32, message = "queue backend too long")
            String backend,
            @Positive(message = "mediaMetadataFixedDelayMs must be positive")
            long mediaMetadataFixedDelayMs,
            @Positive(message = "mediaMetadataInitialDelayMs must be positive")
            long mediaMetadataInitialDelayMs
    ) {
    }

    public record AppearanceSection(
            boolean supported
    ) {
    }

    public record ServerSection(
            @NotBlank(message = "storageProvider cannot be blank")
            @Size(max = 32, message = "storageProvider too long")
            String storageProvider,
            boolean redisEnabled
    ) {
    }
}
