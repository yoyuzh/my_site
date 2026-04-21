package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.infra.cache.RedisCacheNames;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminApi;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminStoragePolicyQueryService {

    private final StoragePolicyAdminApi storagePolicyAdminApi;

    @Cacheable(cacheNames = RedisCacheNames.STORAGE_POLICIES, key = "'all'")
    public List<AdminStoragePolicyResponse> listStoragePolicies() {
        return storagePolicyAdminApi.listStoragePoliciesAsAdmin().stream()
                .map(AdminStoragePolicyResponses::from)
                .toList();
    }
}
