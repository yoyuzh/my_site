package com.yoyuzh.transfer.internal.infra;

import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RemoteDownloadTaskRepository extends JpaRepository<RemoteDownloadTask, Long> {

    List<RemoteDownloadTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<RemoteDownloadTask> findByIdAndUserId(Long id, Long userId);
}
