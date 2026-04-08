package com.yoyuzh.files;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileShareLinkRepository extends JpaRepository<FileShareLink, Long> {

    @EntityGraph(attributePaths = {"owner", "file", "file.user", "file.blob"})
    Optional<FileShareLink> findByToken(String token);

    @EntityGraph(attributePaths = {"owner", "file", "file.user", "file.blob"})
    Page<FileShareLink> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "file", "file.user", "file.blob"})
    Optional<FileShareLink> findByIdAndOwnerId(Long id, Long ownerId);
}
