package com.yoyuzh.files.upload;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.platform.storage.api.UploadModePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(uploadModePolicy.resolveUploadMode(capabilities)).thenReturn(UploadSessionUploadMode.DIRECT_SINGLE);

        UploadSessionUploadMode uploadMode = uploadPolicyResolver.resolveUploadMode(capabilities);

        assertThat(uploadMode).isEqualTo(UploadSessionUploadMode.DIRECT_SINGLE);
        verify(uploadModePolicy).resolveUploadMode(capabilities);
    }

    @Test
    void shouldDelegateEffectiveMaxUploadSizeResolution() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("encoded");
        user.setCreatedAt(LocalDateTime.now());
        StoragePolicy storagePolicy = new StoragePolicy();
        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true, true, true, true, false, true, true, false, 512L
        );
        when(uploadConstraintPolicy.resolveEffectiveMaxUploadSize(2_000L, user, storagePolicy, capabilities))
                .thenReturn(512L);

        long effectiveMax = uploadPolicyResolver.resolveEffectiveMaxUploadSize(2_000L, user, storagePolicy, capabilities);

        assertThat(effectiveMax).isEqualTo(512L);
        verify(uploadConstraintPolicy).resolveEffectiveMaxUploadSize(2_000L, user, storagePolicy, capabilities);
    }
}
