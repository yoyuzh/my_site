package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeUploadConstraintPolicyTest {

    private final RuntimeUploadConstraintPolicy policy = new RuntimeUploadConstraintPolicy();

    @Test
    void shouldResolveEffectiveMaxUploadSizeFromAllConstraints() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("encoded");
        user.setCreatedAt(LocalDateTime.now());
        user.setMaxUploadSizeBytes(1_500L);

        StoragePolicy storagePolicy = new StoragePolicy();
        storagePolicy.setMaxSizeBytes(1_200L);

        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true, true, true, true, false, true, true, false, 900L
        );

        long effectiveMax = policy.resolveEffectiveMaxUploadSize(2_000L, user, storagePolicy, capabilities);

        assertThat(effectiveMax).isEqualTo(900L);
    }
}
