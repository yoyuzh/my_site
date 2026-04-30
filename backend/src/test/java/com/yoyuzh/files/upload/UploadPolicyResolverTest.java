package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.files.upload.api.UploadSessionUploadMode;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StorageUploadMode;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.platform.storage.api.UploadModePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadPolicyResolverTest {

    @Mock
    private UploadModePolicy uploadModePolicy;

    @Mock
    private UploadConstraintPolicy uploadConstraintPolicy;

    @InjectMocks
    private UploadPolicyResolver uploadPolicyResolver;

    @Test
    void shouldDelegateUploadModeResolution() {
        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true, false, true, true, false, true, true, false, 1024L
        );
        when(uploadModePolicy.resolveUploadMode(any())).thenReturn(StorageUploadMode.DIRECT_SINGLE);

        UploadSessionUploadMode uploadMode = uploadPolicyResolver.resolveUploadMode(capabilities);

        assertThat(uploadMode).isEqualTo(UploadSessionUploadMode.DIRECT_SINGLE);
        verify(uploadModePolicy).resolveUploadMode(any(com.yoyuzh.platform.storage.api.StoragePolicyCapabilities.class));
    }

    @Test
    void shouldDelegateEffectiveMaxUploadSizeResolution() {
        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true, true, true, true, false, true, true, false, 512L
        );
        when(uploadConstraintPolicy.resolveEffectiveMaxUploadSize(2_000L, 1_000L, 800L, capabilities.maxObjectSize()))
                .thenReturn(512L);

        long effectiveMax = uploadPolicyResolver.resolveEffectiveMaxUploadSize(
                2_000L,
                1_000L,
                800L,
                capabilities
        );

        assertThat(effectiveMax).isEqualTo(512L);
        verify(uploadConstraintPolicy).resolveEffectiveMaxUploadSize(2_000L, 1_000L, 800L, capabilities.maxObjectSize());
    }
}
