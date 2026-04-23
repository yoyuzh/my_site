package com.yoyuzh.files.sharing.internal.application;

import com.yoyuzh.files.sharing.api.SharingAdminMetricsApi;
import com.yoyuzh.files.sharing.internal.infra.FileShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimeSharingAdminMetricsApi implements SharingAdminMetricsApi {

    private final FileShareLinkRepository fileShareLinkRepository;

    @Override
    @Transactional(readOnly = true)
    public long totalDownloadCountAsAdmin() {
        return fileShareLinkRepository.sumDownloadCountAsAdmin();
    }
}
