package com.yoyuzh.ops.admin.internal.application.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AdminConfigRegistryTest {

    private final AdminConfigRegistry registry = new AdminConfigRegistry();

    @Test
    void shouldRegisterUniqueSafeFirstPassKeys() {
        List<AdminConfigDefinition> definitions = registry.definitions();
        List<String> keys = definitions.stream()
                .map(AdminConfigDefinition::key)
                .toList();

        assertThat(keys).containsExactly(
                "registration.inviteCodeRequired",
                "registration.currentInviteCode",
                "registration.managementRoles",
                "transfer.offlineTransferStorageLimitBytes",
                "media.metadataExtractionEnabled",
                "media.thumbnailGenerationEnabled",
                "media.videoPosterEnabled",
                "queue.backend",
                "queue.mediaMetadataFixedDelayMs",
                "queue.mediaMetadataInitialDelayMs",
                "server.storageProvider",
                "server.redisEnabled"
        );
        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void shouldNotRegisterStartupSecretKeys() {
        Set<String> keys = registry.definitions().stream()
                .map(AdminConfigDefinition::key)
                .collect(Collectors.toSet());

        assertThat(keys).noneMatch(this::isStartupSecretKey);
    }

    private boolean isStartupSecretKey(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("accesskey")
                || normalized.contains("access-key")
                || normalized.contains("secretkey")
                || normalized.contains("secret-key")
                || normalized.contains("viewer-token")
                || normalized.contains("jwt")
                || normalized.contains("ssh")
                || normalized.contains("credential")
                || normalized.contains("db.url");
    }
}
