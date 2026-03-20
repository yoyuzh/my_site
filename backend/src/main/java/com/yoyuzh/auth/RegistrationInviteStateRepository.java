package com.yoyuzh.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface RegistrationInviteStateRepository extends JpaRepository<RegistrationInviteState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from RegistrationInviteState state where state.id = :id")
    Optional<RegistrationInviteState> findByIdForUpdate(@Param("id") Long id);
}
