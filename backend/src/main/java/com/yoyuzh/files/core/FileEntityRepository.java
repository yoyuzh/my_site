package com.yoyuzh.files.core;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileEntityRepository extends JpaRepository<FileEntity, Long> {

    Optional<FileEntity> findByObjectKeyAndEntityType(String objectKey, FileEntityType entityType);

    long countByStoragePolicyIdAndEntityType(Long storagePolicyId, FileEntityType entityType);

    List<FileEntity> findByStoragePolicyIdAndEntityTypeOrderByIdAsc(Long storagePolicyId, FileEntityType entityType);
}
