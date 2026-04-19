package com.yoyuzh.ops.admin.api;

import java.util.List;

public record AdminSettingsResponse(
        SiteSection site,
        RegistrationSection registration,
        UserSessionSection userSession,
        TransferSection transfer,
        MediaProcessingSection mediaProcessing,
        QueueSection queue,
        AppearanceSection appearance,
        ServerSection server
) {

    public record SiteSection(
            boolean supported,
            boolean writeSupported
    ) {
    }

    public record RegistrationSection(
            boolean inviteCodeRequired,
            String currentInviteCode,
            List<String> managementRoles,
            boolean writeSupported
    ) {
    }

    public record UserSessionSection(
            long accessExpirationSeconds,
            long refreshExpirationSeconds,
            boolean tokenBlacklistEnabled,
            long tokenBlacklistTtlBufferSeconds,
            boolean writeSupported
    ) {
    }

    public record TransferSection(
            long offlineTransferStorageLimitBytes,
            boolean writeSupported
    ) {
    }

    public record MediaProcessingSection(
            boolean metadataExtractionEnabled,
            boolean thumbnailGenerationEnabled,
            boolean videoPosterEnabled,
            boolean writeSupported
    ) {
    }

    public record QueueSection(
            String backend,
            long mediaMetadataFixedDelayMs,
            long mediaMetadataInitialDelayMs,
            boolean writeSupported
    ) {
    }

    public record AppearanceSection(
            boolean supported,
            boolean writeSupported
    ) {
    }

    public record ServerSection(
            String storageProvider,
            boolean redisEnabled,
            boolean writeSupported
    ) {
    }
}
