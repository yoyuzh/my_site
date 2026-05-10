package com.yoyuzh.ops.admin.internal.infra.config;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminConfigChangeLogRepository extends JpaRepository<AdminConfigChangeLogEntity, Long> {

    Page<AdminConfigChangeLogEntity> findByConfigKeyOrderByVersionDesc(String configKey, Pageable pageable);

    Optional<AdminConfigChangeLogEntity> findFirstByConfigKeyAndVersion(String configKey, long version);
}
