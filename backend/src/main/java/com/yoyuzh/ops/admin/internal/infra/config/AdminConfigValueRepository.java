package com.yoyuzh.ops.admin.internal.infra.config;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminConfigValueRepository extends JpaRepository<AdminConfigValueEntity, Long> {

    Optional<AdminConfigValueEntity> findByConfigKey(String configKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from AdminConfigValueEntity value where value.configKey = :configKey")
    Optional<AdminConfigValueEntity> findByConfigKeyForUpdate(@Param("configKey") String configKey);
}
