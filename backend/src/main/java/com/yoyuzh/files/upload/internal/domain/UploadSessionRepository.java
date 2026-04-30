package com.yoyuzh.files.upload.internal.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadSessionRepository extends JpaRepository<UploadSession, Long> {

    Optional<UploadSession> findBySessionIdAndUserId(String sessionId, Long userId);

    List<UploadSession> findByStatusInAndExpiresAtBefore(List<UploadSessionStatus> statuses, LocalDateTime expiresAt);
}
