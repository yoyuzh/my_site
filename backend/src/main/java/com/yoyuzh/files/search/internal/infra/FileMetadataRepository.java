package com.yoyuzh.files.search.internal.infra;

import com.yoyuzh.files.search.internal.domain.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Optional<FileMetadata> findByFileIdAndName(Long fileId, String name);
}
