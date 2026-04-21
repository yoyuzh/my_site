package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StorageUploadMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeUploadModePolicyTest {

    private final RuntimeUploadModePolicy policy = new RuntimeUploadModePolicy();

    @Test
    void shouldUseProxyWhenDirectUploadIsDisabled() {
        StorageUploadMode uploadMode = policy.resolveUploadMode(new StoragePolicyCapabilities(
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

        assertThat(uploadMode).isEqualTo(StorageUploadMode.PROXY);
    }

    @Test
    void shouldUseDirectMultipartWhenMultipartUploadIsEnabled() {
        StorageUploadMode uploadMode = policy.resolveUploadMode(new StoragePolicyCapabilities(
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

        assertThat(uploadMode).isEqualTo(StorageUploadMode.DIRECT_MULTIPART);
    }

    @Test
    void shouldUseDirectSingleWhenDirectUploadIsEnabledWithoutMultipart() {
        StorageUploadMode uploadMode = policy.resolveUploadMode(new StoragePolicyCapabilities(
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

        assertThat(uploadMode).isEqualTo(StorageUploadMode.DIRECT_SINGLE);
    }
}
