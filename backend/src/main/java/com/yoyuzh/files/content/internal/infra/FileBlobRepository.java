package com.yoyuzh.files.content.internal.infra;

import com.yoyuzh.files.content.internal.domain.FileBlob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FileBlobRepository extends JpaRepository<FileBlob, Long> {

    Optional<FileBlob> findByObjectKey(String objectKey);

    List<FileBlob> findAllByObjectKeyIn(Collection<String> objectKeys);

    @Query("""
            select coalesce(sum(b.size), 0)
            from FileBlob b
            """)
    long sumAllBlobSize();
}
