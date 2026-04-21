package com.yoyuzh.platform.storage.internal.infra;

import com.yoyuzh.platform.storage.internal.domain.StoragePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoragePolicyRepository extends JpaRepository<StoragePolicy, Long> {

    Optional<StoragePolicy> findFirstByDefaultPolicyTrueOrderByIdAsc();
}
