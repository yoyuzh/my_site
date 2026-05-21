package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.files.upload.api.UploadSessionUploadMode;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeUploadSessionTransportPolicyTest {

    @Test
    void shouldNotForceTusForRegularProxyUploadSessions() {
        StoragePolicyQuery storagePolicyQuery = mock(StoragePolicyQuery.class);
        UploadPolicyResolver uploadPolicyResolver = new UploadPolicyResolver(
                UploadPolicyResolver::resolveDefaultUploadMode,
                UploadPolicyResolver::resolveDefaultEffectiveMaxUploadSize
        );
        UploadSessionTusService uploadSessionTusService = mock(UploadSessionTusService.class);
        RuntimeUploadSessionTransportPolicy policy = new RuntimeUploadSessionTransportPolicy(
                storagePolicyQuery,
                uploadPolicyResolver,
                uploadSessionTusService
        );

        when(storagePolicyQuery.readPolicyDescriptor(42L)).thenReturn(new StoragePolicyDescriptor(
                42L,
                "Local",
                StoragePolicyType.LOCAL,
                null,
                null,
                null,
                true,
                "",
                null,
                true,
                0L
        ));
        when(uploadSessionTusService.supportsTus(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertThat(policy.usesTusUpload(42L)).isFalse();
    }
}
