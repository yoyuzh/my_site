package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeStoragePolicyBlobAccessApiTest {

    @Test
    void shouldReuseCachedStorageForSamePolicyAcrossBlobOperations() {
        FileStorageProperties properties = new FileStorageProperties();
        AtomicInteger factoryCalls = new AtomicInteger();
        FileContentStorage storage = mock(FileContentStorage.class);
        byte[] payload = "payload".getBytes();
        when(storage.readBlob("blobs/source-1")).thenReturn(payload);

        RuntimeStoragePolicyBlobAccessApi api = new RuntimeStoragePolicyBlobAccessApi(
                properties,
                ignored -> {
                    factoryCalls.incrementAndGet();
                    return storage;
                }
        );

        StoragePolicyDescriptor policy = new StoragePolicyDescriptor(
                1L,
                "Archive Local",
                StoragePolicyType.LOCAL,
                null,
                null,
                null,
                true,
                "/tmp/archive",
                StoragePolicyCredentialMode.NONE,
                true,
                0L
        );

        api.readBlob(policy, "blobs/source-1");
        api.storeBlob(policy, "blobs/target-1", "text/plain", payload);
        api.deleteBlob(policy, "blobs/target-1");

        assertThat(factoryCalls).hasValue(1);
        verify(storage).readBlob("blobs/source-1");
        verify(storage).storeBlob("blobs/target-1", "text/plain", payload);
        verify(storage).deleteBlob("blobs/target-1");
    }
}
