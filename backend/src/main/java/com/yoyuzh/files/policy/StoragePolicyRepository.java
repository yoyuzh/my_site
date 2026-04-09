package com.yoyuzh.files.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoragePolicyRepository extends JpaRepository<StoragePolicy, Long> {

    Optional<StoragePolicy> findFirstByDefaultPolicyTrueOrderByIdAsc();
}
