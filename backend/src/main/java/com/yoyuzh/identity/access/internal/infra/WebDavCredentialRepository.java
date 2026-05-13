package com.yoyuzh.identity.access.internal.infra;

import com.yoyuzh.identity.access.internal.domain.WebDavCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebDavCredentialRepository extends JpaRepository<WebDavCredential, Long> {

    Optional<WebDavCredential> findByUserId(Long userId);
}
