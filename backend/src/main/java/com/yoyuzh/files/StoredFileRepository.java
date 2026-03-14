package com.yoyuzh.files;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    @Query("""
            select case when count(f) > 0 then true else false end
            from StoredFile f
            where f.user.id = :userId and f.path = :path and f.filename = :filename
            """)
    boolean existsByUserIdAndPathAndFilename(@Param("userId") Long userId,
                                             @Param("path") String path,
                                             @Param("filename") String filename);

    @Query("""
            select f from StoredFile f
            where f.user.id = :userId and f.path = :path
            order by f.directory desc, f.createdAt desc
            """)
    Page<StoredFile> findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(@Param("userId") Long userId,
                                                                          @Param("path") String path,
                                                                          Pageable pageable);

    List<StoredFile> findTop12ByUserIdAndDirectoryFalseOrderByCreatedAtDesc(Long userId);
}
