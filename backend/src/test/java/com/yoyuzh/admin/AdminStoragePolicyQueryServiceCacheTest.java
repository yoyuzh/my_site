package com.yoyuzh.admin;

import com.yoyuzh.config.RedisCacheNames;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyCredentialMode;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.policy.StoragePolicyType;
import com.yoyuzh.files.tasks.BackgroundTaskCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(AdminStoragePolicyQueryServiceCacheTest.CacheTestConfiguration.class)
class AdminStoragePolicyQueryServiceCacheTest {

    @Autowired
    private AdminStoragePolicyQueryService adminStoragePolicyQueryService;

    @Autowired
    private AdminStorageGovernanceService adminStorageGovernanceService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StoragePolicyRepository storagePolicyRepository;

    @Autowired
    private StoragePolicyService storagePolicyService;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(RedisCacheNames.STORAGE_POLICIES).clear();
        reset(storagePolicyRepository, storagePolicyService);
        when(storagePolicyService.readCapabilities(any(StoragePolicy.class))).thenReturn(defaultCapabilities());
    }

    @Test
    void shouldCacheStoragePolicyListUntilExplicitEviction() {
        StoragePolicy defaultPolicy = storagePolicy(1L, "Default Local Storage", true, true);
        when(storagePolicyRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(defaultPolicy));

        adminStoragePolicyQueryService.listStoragePolicies();
        adminStoragePolicyQueryService.listStoragePolicies();

        verify(storagePolicyRepository, times(1)).findAll(any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void shouldEvictStoragePolicyListAfterCreatingPolicy() {
        StoragePolicy defaultPolicy = storagePolicy(1L, "Default Local Storage", true, true);
        StoragePolicy createdPolicy = storagePolicy(2L, "Archive Bucket", true, false);
        when(storagePolicyRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(defaultPolicy))
                .thenReturn(List.of(defaultPolicy, createdPolicy));
        when(storagePolicyRepository.save(any(StoragePolicy.class))).thenAnswer(invocation -> {
            StoragePolicy saved = invocation.getArgument(0);
            saved.setId(2L);
            saved.setCreatedAt(LocalDateTime.now());
            saved.setUpdatedAt(LocalDateTime.now());
            return saved;
        });

        adminStoragePolicyQueryService.listStoragePolicies();
        adminStorageGovernanceService.createStoragePolicy(upsertRequest("Archive Bucket", true));
        adminStoragePolicyQueryService.listStoragePolicies();

        verify(storagePolicyRepository, times(2)).findAll(any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void shouldEvictStoragePolicyListAfterUpdatingPolicy() {
        StoragePolicy existingPolicy = storagePolicy(2L, "Archive Bucket", true, false);
        StoragePolicy updatedPolicy = storagePolicy(2L, "Hot Bucket", true, false);
        when(storagePolicyRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(existingPolicy))
                .thenReturn(List.of(updatedPolicy));
        when(storagePolicyRepository.findById(2L)).thenReturn(Optional.of(existingPolicy));
        when(storagePolicyRepository.save(existingPolicy)).thenReturn(updatedPolicy);

        adminStoragePolicyQueryService.listStoragePolicies();
        adminStorageGovernanceService.updateStoragePolicy(2L, upsertRequest("Hot Bucket", true));
        adminStoragePolicyQueryService.listStoragePolicies();

        verify(storagePolicyRepository, times(2)).findAll(any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void shouldEvictStoragePolicyListAfterUpdatingPolicyStatus() {
        StoragePolicy existingPolicy = storagePolicy(2L, "Archive Bucket", true, false);
        StoragePolicy disabledPolicy = storagePolicy(2L, "Archive Bucket", false, false);
        when(storagePolicyRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(existingPolicy))
                .thenReturn(List.of(disabledPolicy));
        when(storagePolicyRepository.findById(2L)).thenReturn(Optional.of(existingPolicy));
        when(storagePolicyRepository.save(existingPolicy)).thenReturn(disabledPolicy);

        adminStoragePolicyQueryService.listStoragePolicies();
        adminStorageGovernanceService.updateStoragePolicyStatus(2L, false);
        adminStoragePolicyQueryService.listStoragePolicies();

        verify(storagePolicyRepository, times(2)).findAll(any(org.springframework.data.domain.Sort.class));
    }

    private StoragePolicy storagePolicy(Long id, String name, boolean enabled, boolean defaultPolicy) {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(id);
        policy.setName(name);
        policy.setType(StoragePolicyType.LOCAL);
        policy.setCredentialMode(StoragePolicyCredentialMode.NONE);
        policy.setMaxSizeBytes(1024L);
        policy.setEnabled(enabled);
        policy.setDefaultPolicy(defaultPolicy);
        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());
        return policy;
    }

    private AdminStoragePolicyUpsertRequest upsertRequest(String name, boolean enabled) {
        return new AdminStoragePolicyUpsertRequest(
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
        StoragePolicyRepository storagePolicyRepository() {
            return mock(StoragePolicyRepository.class);
        }

        @Bean
        StoragePolicyService storagePolicyService() {
            return mock(StoragePolicyService.class);
        }

        @Bean
        FileEntityRepository fileEntityRepository() {
            return mock(FileEntityRepository.class);
        }

        @Bean
        StoredFileEntityRepository storedFileEntityRepository() {
            return mock(StoredFileEntityRepository.class);
        }

        @Bean
        BackgroundTaskCommandService backgroundTaskCommandService() {
            return mock(BackgroundTaskCommandService.class);
        }

        @Bean
        AdminAuditService adminAuditService() {
            return mock(AdminAuditService.class);
        }

        @Bean
        AdminStoragePolicyQueryService adminStoragePolicyQueryService(StoragePolicyRepository storagePolicyRepository,
                                                                      StoragePolicyService storagePolicyService) {
            return new AdminStoragePolicyQueryService(storagePolicyRepository, storagePolicyService);
        }

        @Bean
        AdminStorageGovernanceService adminStorageGovernanceService(StoragePolicyRepository storagePolicyRepository,
                                                                    StoragePolicyService storagePolicyService,
                                                                    FileEntityRepository fileEntityRepository,
                                                                    StoredFileEntityRepository storedFileEntityRepository,
                                                                    BackgroundTaskCommandService backgroundTaskCommandService,
                                                                    AdminAuditService adminAuditService) {
            return new AdminStorageGovernanceService(
                    storagePolicyRepository,
                    storagePolicyService,
                    fileEntityRepository,
                    storedFileEntityRepository,
                    backgroundTaskCommandService,
                    adminAuditService
            );
        }
    }
}
