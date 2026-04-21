package com.yoyuzh.ops.admin.internal.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.infra.cache.RedisCacheNames;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminApi;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminView;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AdminStoragePolicyQueryServiceCacheTest.CacheTestConfiguration.class)
class AdminStoragePolicyQueryServiceCacheTest {

    @Autowired
    private AdminStoragePolicyQueryService adminStoragePolicyQueryService;

    @Autowired
    private AdminStorageGovernanceService adminStorageGovernanceService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StoragePolicyAdminApi storagePolicyAdminApi;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(RedisCacheNames.STORAGE_POLICIES).clear();
        reset(storagePolicyAdminApi);
    }

    @Test
    void shouldCacheStoragePolicyListUntilExplicitEviction() {
        StoragePolicyAdminView defaultPolicy = storagePolicy(1L, "Default Local Storage", true, true);
        when(storagePolicyAdminApi.listStoragePoliciesAsAdmin()).thenReturn(List.of(defaultPolicy));

        adminStoragePolicyQueryService.listStoragePolicies();
        adminStoragePolicyQueryService.listStoragePolicies();

        verify(storagePolicyAdminApi, times(1)).listStoragePoliciesAsAdmin();
    }

    @Test
    void shouldEvictStoragePolicyListAfterCreatingPolicy() {
        StoragePolicyAdminView defaultPolicy = storagePolicy(1L, "Default Local Storage", true, true);
        StoragePolicyAdminView createdPolicy = storagePolicy(2L, "Archive Bucket", true, false);
        when(storagePolicyAdminApi.listStoragePoliciesAsAdmin())
                .thenReturn(List.of(defaultPolicy))
                .thenReturn(List.of(defaultPolicy, createdPolicy));
        when(storagePolicyAdminApi.createStoragePolicyAsAdmin(any()))
                .thenReturn(createdPolicy);

        adminStoragePolicyQueryService.listStoragePolicies();
        adminStorageGovernanceService.createStoragePolicy(upsertRequest("Archive Bucket", true));
        adminStoragePolicyQueryService.listStoragePolicies();

        verify(storagePolicyAdminApi, times(2)).listStoragePoliciesAsAdmin();
    }

    @Test
    void shouldEvictStoragePolicyListAfterUpdatingPolicy() {
        StoragePolicyAdminView existingPolicy = storagePolicy(2L, "Archive Bucket", true, false);
        StoragePolicyAdminView updatedPolicy = storagePolicy(2L, "Hot Bucket", true, false);
        when(storagePolicyAdminApi.listStoragePoliciesAsAdmin())
                .thenReturn(List.of(existingPolicy))
                .thenReturn(List.of(updatedPolicy));
        when(storagePolicyAdminApi.updateStoragePolicyAsAdmin(any(), any()))
                .thenReturn(updatedPolicy);

        adminStoragePolicyQueryService.listStoragePolicies();
        adminStorageGovernanceService.updateStoragePolicy(2L, upsertRequest("Hot Bucket", true));
        adminStoragePolicyQueryService.listStoragePolicies();

        verify(storagePolicyAdminApi, times(2)).listStoragePoliciesAsAdmin();
    }

    @Test
    void shouldEvictStoragePolicyListAfterUpdatingPolicyStatus() {
        StoragePolicyAdminView existingPolicy = storagePolicy(2L, "Archive Bucket", true, false);
        StoragePolicyAdminView disabledPolicy = storagePolicy(2L, "Archive Bucket", false, false);
        when(storagePolicyAdminApi.listStoragePoliciesAsAdmin())
                .thenReturn(List.of(existingPolicy))
                .thenReturn(List.of(disabledPolicy));
        when(storagePolicyAdminApi.updateStoragePolicyStatusAsAdmin(2L, false))
                .thenReturn(disabledPolicy);

        adminStoragePolicyQueryService.listStoragePolicies();
        adminStorageGovernanceService.updateStoragePolicyStatus(2L, false);
        adminStoragePolicyQueryService.listStoragePolicies();

        verify(storagePolicyAdminApi, times(2)).listStoragePoliciesAsAdmin();
    }

    private StoragePolicyAdminView storagePolicy(Long id, String name, boolean enabled, boolean defaultPolicy) {
        return new StoragePolicyAdminView(
                id,
                name,
                StoragePolicyType.LOCAL,
                null,
                null,
                null,
                false,
                "",
                StoragePolicyCredentialMode.NONE,
                1024L,
                defaultCapabilities(),
                enabled,
                defaultPolicy,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private AdminStoragePolicyUpsertInput upsertRequest(String name, boolean enabled) {
        return new AdminStoragePolicyUpsertInput(
                name,
                StoragePolicyType.LOCAL,
                null,
                null,
                null,
                false,
                "",
                StoragePolicyCredentialMode.NONE,
                1024L,
                defaultCapabilities(),
                enabled
        );
    }

    private StoragePolicyCapabilities defaultCapabilities() {
        return new StoragePolicyCapabilities(false, false, false, true, false, true, false, false, 1024L);
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(RedisCacheNames.STORAGE_POLICIES);
        }

        @Bean
        StoragePolicyAdminApi storagePolicyAdminApi() {
            return mock(StoragePolicyAdminApi.class);
        }

        @Bean
        BackgroundTaskLifecycleApi backgroundTaskLifecycleApi() {
            return mock(BackgroundTaskLifecycleApi.class);
        }

        @Bean
        AdminAuditService adminAuditService() {
            return mock(AdminAuditService.class);
        }

        @Bean
        AdminStoragePolicyQueryService adminStoragePolicyQueryService(StoragePolicyAdminApi storagePolicyAdminApi) {
            return new AdminStoragePolicyQueryService(storagePolicyAdminApi);
        }

        @Bean
        AdminStorageGovernanceService adminStorageGovernanceService(StoragePolicyAdminApi storagePolicyAdminApi,
                                                                    BackgroundTaskLifecycleApi backgroundTaskLifecycleApi,
                                                                    AdminAuditService adminAuditService) {
            return new AdminStorageGovernanceService(
                    storagePolicyAdminApi,
                    backgroundTaskLifecycleApi,
                    adminAuditService
            );
        }
    }
}
