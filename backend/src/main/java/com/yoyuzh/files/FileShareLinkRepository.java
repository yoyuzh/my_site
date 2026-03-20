package com.yoyuzh.files;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileShareLinkRepository extends JpaRepository<FileShareLink, Long> {

    @EntityGraph(attributePaths = {"owner", "file", "file.user"})
    Optional<FileShareLink> findByToken(String token);
}
