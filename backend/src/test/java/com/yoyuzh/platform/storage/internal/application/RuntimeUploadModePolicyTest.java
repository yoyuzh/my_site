package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.upload.UploadSessionUploadMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeUploadModePolicyTest {

    private final RuntimeUploadModePolicy policy = new RuntimeUploadModePolicy();

    @Test
    void shouldUseProxyWhenDirectUploadIsDisabled() {
        UploadSessionUploadMode uploadMode = policy.resolveUploadMode(new StoragePolicyCapabilities(
                false,
                false,
                false,
                true,
                false,
                true,
                false,
                false,
                1024L
        ));

        assertThat(uploadMode).isEqualTo(UploadSessionUploadMode.PROXY);
    }

    @Test
    void shouldUseDirectMultipartWhenMultipartUploadIsEnabled() {
        UploadSessionUploadMode uploadMode = policy.resolveUploadMode(new StoragePolicyCapabilities(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                1024L
        ));

        assertThat(uploadMode).isEqualTo(UploadSessionUploadMode.DIRECT_MULTIPART);
    }

    @Test
    void shouldUseDirectSingleWhenDirectUploadIsEnabledWithoutMultipart() {
        UploadSessionUploadMode uploadMode = policy.resolveUploadMode(new StoragePolicyCapabilities(
                true,
                false,
                true,
                true,
                false,
                true,
                true,
                false,
                1024L
        ));

        assertThat(uploadMode).isEqualTo(UploadSessionUploadMode.DIRECT_SINGLE);
    }
}
