package com.yoyuzh.platform.storage.internal.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeUploadConstraintPolicyTest {

    private final RuntimeUploadConstraintPolicy policy = new RuntimeUploadConstraintPolicy();

    @Test
    void shouldResolveEffectiveMaxUploadSizeFromAllConstraints() {
        long effectiveMax = policy.resolveEffectiveMaxUploadSize(2_000L, 1_500L, 1_200L, 900L);

        assertThat(effectiveMax).isEqualTo(900L);
    }
}
