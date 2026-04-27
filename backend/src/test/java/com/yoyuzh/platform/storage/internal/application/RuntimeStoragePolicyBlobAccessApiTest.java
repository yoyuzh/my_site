package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void shouldEvictLeastRecentlyUsedStorageWhenCacheIsFull() {
        FileStorageProperties properties = new FileStorageProperties();
        AtomicInteger factoryCalls = new AtomicInteger();
        TestStorage[] created = new TestStorage[4];
        RuntimeStoragePolicyBlobAccessApi api = new RuntimeStoragePolicyBlobAccessApi(
                properties,
                policy -> {
                    TestStorage storage = new TestStorage();
                    created[factoryCalls.getAndIncrement()] = storage;
                    return storage;
                },
                2
        );

        StoragePolicyDescriptor first = localPolicy(1L, "/tmp/one");
        StoragePolicyDescriptor second = localPolicy(2L, "/tmp/two");
        StoragePolicyDescriptor third = localPolicy(3L, "/tmp/three");

        api.deleteBlob(first, "blobs/one");
        api.deleteBlob(second, "blobs/two");
        api.deleteBlob(third, "blobs/three");
        api.deleteBlob(first, "blobs/one");

        assertThat(factoryCalls).hasValue(4);
        assertThat(created[0].closed).isTrue();
        assertThat(created[1].closed).isTrue();
        assertThat(created[2].closed).isFalse();
        assertThat(created[3].closed).isFalse();
    }

    @Test
    void shouldStreamBlobOperationsWithoutBufferingReadBlob() {
        FileStorageProperties properties = new FileStorageProperties();
        FileContentStorage storage = mock(FileContentStorage.class);
        RuntimeStoragePolicyBlobAccessApi api = new RuntimeStoragePolicyBlobAccessApi(
                properties,
                ignored -> storage,
                2
        );
        StoragePolicyDescriptor policy = localPolicy(1L, "/tmp/archive");
        byte[] payload = "payload".getBytes();

        api.openBlobStream(policy, "blobs/source-1");
        api.storeBlob(policy, "blobs/target-1", "text/plain", new ByteArrayInputStream(payload), payload.length);

        verify(storage).readBlobStream("blobs/source-1");
        verify(storage).storeBlob(org.mockito.ArgumentMatchers.eq("blobs/target-1"), org.mockito.ArgumentMatchers.eq("text/plain"), org.mockito.ArgumentMatchers.any(InputStream.class), org.mockito.ArgumentMatchers.eq((long) payload.length));
        verify(storage, never()).readBlob("blobs/source-1");
    }

    @Test
    void shouldRejectTraversingObjectKeysBeforeTouchingStorage() {
        FileStorageProperties properties = new FileStorageProperties();
        FileContentStorage storage = mock(FileContentStorage.class);
        RuntimeStoragePolicyBlobAccessApi api = new RuntimeStoragePolicyBlobAccessApi(
                properties,
                ignored -> storage,
                2
        );
        StoragePolicyDescriptor policy = localPolicy(1L, "/tmp/archive");

        assertThatThrownBy(() -> api.openBlobStream(policy, "../etc/passwd"))
                .isInstanceOf(com.yoyuzh.shared.kernel.BusinessException.class)
                .hasMessageContaining("Invalid storage object key");

        verify(storage, never()).readBlobStream("../etc/passwd");
    }

    private StoragePolicyDescriptor localPolicy(Long id, String prefix) {
        return new StoragePolicyDescriptor(
                id,
                "Policy-" + id,
                StoragePolicyType.LOCAL,
                null,
                null,
                null,
                true,
                prefix,
                StoragePolicyCredentialMode.NONE,
                true,
                0L
        );
    }

    private static final class TestStorage implements FileContentStorage, AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            this.closed = true;
        }

        @Override public com.yoyuzh.files.storage.PreparedUpload prepareUpload(Long userId, String path, String storageName, String contentType, long size) { throw new UnsupportedOperationException(); }
        @Override public void upload(Long userId, String path, String storageName, org.springframework.web.multipart.MultipartFile file) { throw new UnsupportedOperationException(); }
        @Override public void completeUpload(Long userId, String path, String storageName, String contentType, long size) { throw new UnsupportedOperationException(); }
        @Override public byte[] readFile(Long userId, String path, String storageName) { throw new UnsupportedOperationException(); }
        @Override public void deleteFile(Long userId, String path, String storageName) { throw new UnsupportedOperationException(); }
        @Override public String createDownloadUrl(Long userId, String path, String storageName, String filename) { throw new UnsupportedOperationException(); }
        @Override public com.yoyuzh.files.storage.PreparedUpload prepareBlobUpload(String path, String filename, String objectKey, String contentType, long size) { throw new UnsupportedOperationException(); }
        @Override public void uploadBlob(String objectKey, org.springframework.web.multipart.MultipartFile file) { throw new UnsupportedOperationException(); }
        @Override public void completeBlobUpload(String objectKey, String contentType, long size) { throw new UnsupportedOperationException(); }
        @Override public void storeBlob(String objectKey, String contentType, byte[] content) { }
        @Override public byte[] readBlob(String objectKey) { return new byte[0]; }
        @Override public InputStream readBlobStream(String objectKey) { return new ByteArrayInputStream(new byte[0]); }
        @Override public void deleteBlob(String objectKey) { }
        @Override public String createBlobDownloadUrl(String objectKey, String filename) { throw new UnsupportedOperationException(); }
        @Override public void createDirectory(Long userId, String logicalPath) { throw new UnsupportedOperationException(); }
        @Override public void ensureDirectory(Long userId, String logicalPath) { throw new UnsupportedOperationException(); }
        @Override public void storeTransferFile(String sessionId, String storageName, String contentType, byte[] content) { throw new UnsupportedOperationException(); }
        @Override public byte[] readTransferFile(String sessionId, String storageName) { throw new UnsupportedOperationException(); }
        @Override public void deleteTransferFile(String sessionId, String storageName) { throw new UnsupportedOperationException(); }
        @Override public String createTransferDownloadUrl(String sessionId, String storageName, String filename) { throw new UnsupportedOperationException(); }
        @Override public boolean supportsDirectDownload() { return false; }
        @Override public String resolveLegacyFileObjectKey(Long userId, String path, String storageName) { throw new UnsupportedOperationException(); }
    }
}
