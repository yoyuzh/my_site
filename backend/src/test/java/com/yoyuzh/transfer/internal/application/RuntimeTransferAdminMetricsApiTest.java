package com.yoyuzh.transfer.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yoyuzh.transfer.internal.infra.OfflineTransferSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuntimeTransferAdminMetricsApiTest {

    @Mock
    private OfflineTransferSessionRepository offlineTransferSessionRepository;

    private RuntimeTransferAdminMetricsApi runtimeTransferAdminMetricsApi;

    @BeforeEach
    void setUp() {
        runtimeTransferAdminMetricsApi = new RuntimeTransferAdminMetricsApi(offlineTransferSessionRepository);
    }

    @Test
    void shouldReadCurrentOfflineStorageBytes() {
        when(offlineTransferSessionRepository.sumUploadedFileSizeByExpiresAtAfter(any())).thenReturn(2048L);

        long storageBytes = runtimeTransferAdminMetricsApi.currentOfflineStorageBytes();

        assertThat(storageBytes).isEqualTo(2048L);
    }
}
