package com.yoyuzh.ops.admin.internal.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminRuntimeSettingsStateRepository extends JpaRepository<AdminRuntimeSettingsState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from AdminRuntimeSettingsState state where state.id = :id")
    Optional<AdminRuntimeSettingsState> findByIdForUpdate(@Param("id") Long id);
}
