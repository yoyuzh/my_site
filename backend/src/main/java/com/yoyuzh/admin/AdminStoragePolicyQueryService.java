package com.yoyuzh.admin;

import com.yoyuzh.config.RedisCacheNames;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminStoragePolicyQueryService {

    private final StoragePolicyRepository storagePolicyRepository;
    private final StoragePolicyService storagePolicyService;

    @Cacheable(cacheNames = RedisCacheNames.STORAGE_POLICIES, key = "'all'")
    public List<AdminStoragePolicyResponse> listStoragePolicies() {
        return storagePolicyRepository.findAll(Sort.by(Sort.Direction.DESC, "defaultPolicy")
                        .and(Sort.by(Sort.Direction.DESC, "enabled"))
                        .and(Sort.by(Sort.Direction.ASC, "id")))
                .stream()
                .map(policy -> AdminStoragePolicyResponses.from(
                        policy,
                        storagePolicyService.readCapabilities(policy)
                ))
                .toList();
    }
}
