package com.yoyuzh.files;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    @EntityGraph(attributePaths = "user")
    Page<StoredFile> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select f from StoredFile f
            join f.user u
            where (:query is null or :query = ''
                or lower(f.filename) like lower(concat('%', :query, '%'))
                or lower(f.path) like lower(concat('%', :query, '%')))
              and (:ownerQuery is null or :ownerQuery = ''
                or lower(u.username) like lower(concat('%', :ownerQuery, '%'))
                or lower(u.email) like lower(concat('%', :ownerQuery, '%')))
            """)
    Page<StoredFile> searchAdminFiles(@Param("query") String query,
                                      @Param("ownerQuery") String ownerQuery,
                                      Pageable pageable);

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
            where f.user.id = :userId and f.path = :path and f.filename = :filename
            """)
    Optional<StoredFile> findByUserIdAndPathAndFilename(@Param("userId") Long userId,
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

    @Query("""
            select f from StoredFile f
            where f.user.id = :userId and (f.path = :path or f.path like concat(:path, '/%'))
            order by f.createdAt asc
            """)
    List<StoredFile> findByUserIdAndPathEqualsOrDescendant(@Param("userId") Long userId,
                                                           @Param("path") String path);

    List<StoredFile> findTop12ByUserIdAndDirectoryFalseOrderByCreatedAtDesc(Long userId);
}
