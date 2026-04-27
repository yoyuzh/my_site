package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.api.TransferRuntimeMetricsPort;
import com.yoyuzh.transfer.internal.domain.OfflineTransferFile;
import com.yoyuzh.transfer.internal.infra.OfflineTransferSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineTransferQuotaServiceTest {

    @Mock
    private OfflineTransferSessionRepository offlineTransferSessionRepository;
    @Mock
    private TransferRuntimeMetricsPort transferRuntimeMetricsPort;

    private OfflineTransferQuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new OfflineTransferQuotaService(offlineTransferSessionRepository, transferRuntimeMetricsPort);
    }

    @Test
    void shouldRejectEmptyOfflineUpload() {
        assertThatThrownBy(() -> quotaService.ensureUploadAllowed(file(10L, false), 0L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void shouldRejectUploadLargerThanPerFileLimit() {
        assertThatThrownBy(() -> quotaService.ensureUploadAllowed(file(10L, false), 101L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);
    }

    @Test
    void shouldRejectUploadSizeThatDoesNotMatchManifest() {
        assertThatThrownBy(() -> quotaService.ensureUploadAllowed(file(10L, false), 9L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void shouldRejectUploadWhenOfflineStorageQuotaWouldBeExceeded() {
        when(offlineTransferSessionRepository.sumUploadedFileSizeByExpiresAtAfter(org.mockito.ArgumentMatchers.any()))
                .thenReturn(90L);
        when(transferRuntimeMetricsPort.offlineTransferStorageLimitBytes()).thenReturn(95L);

        assertThatThrownBy(() -> quotaService.ensureUploadAllowed(file(10L, false), 10L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);
    }

    private OfflineTransferFile file(long size, boolean uploaded) {
        OfflineTransferFile file = new OfflineTransferFile();
        file.setSize(size);
        file.setUploaded(uploaded);
        return file;
    }
}
