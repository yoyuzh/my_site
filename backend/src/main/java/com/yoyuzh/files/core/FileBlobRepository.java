package com.yoyuzh.files.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FileBlobRepository extends JpaRepository<FileBlob, Long> {

    Optional<FileBlob> findByObjectKey(String objectKey);

    @Query("""
            select coalesce(sum(b.size), 0)
            from FileBlob b
            """)
    long sumAllBlobSize();
}
