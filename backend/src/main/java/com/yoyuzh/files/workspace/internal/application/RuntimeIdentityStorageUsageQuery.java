package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.identity.access.api.IdentityStorageUsageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeIdentityStorageUsageQuery implements IdentityStorageUsageQuery {

    private final StoredFileRepository storedFileRepository;

    @Override
    public long usedStorageBytes(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return storedFileRepository.sumFileSizeByUserId(userId);
    }
}
