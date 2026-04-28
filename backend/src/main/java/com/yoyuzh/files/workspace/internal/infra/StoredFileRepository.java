package com.yoyuzh.files.workspace.internal.infra;

import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    interface UserStorageUsageProjection {
        Long getUserId();

        Long getUsedStorageBytes();
    }

    Page<StoredFile> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select f from StoredFile f
            where (:query is null or :query = ''
                or lower(f.filename) like lower(concat('%', :query, '%'))
                or lower(f.path) like lower(concat('%', :query, '%')))
              and f.deletedAt is null
              and (:ownerQuery is null or :ownerQuery = '' or exists (
                    select 1 from User u
                    where u.id = f.userId
                      and (
                          lower(u.username) like lower(concat('%', :ownerQuery, '%'))
                          or lower(u.email) like lower(concat('%', :ownerQuery, '%'))
                      )
              ))
            """)
    Page<StoredFile> searchAdminFiles(@Param("query") String query,
                                      @Param("ownerQuery") String ownerQuery,
                                      Pageable pageable);

    @Query("""
            select case when count(f) > 0 then true else false end
            from StoredFile f
            where f.userId = :userId and f.path = :path and f.filename = :filename and f.deletedAt is null
            """)
    boolean existsByUserIdAndPathAndFilename(@Param("userId") Long userId,
                                             @Param("path") String path,
                                             @Param("filename") String filename);

    @Query("""
            select f.filename from StoredFile f
            where f.userId = :userId
              and f.path = :path
              and f.deletedAt is null
              and (f.filename = :filename or f.filename like concat(:filenamePrefix, '%') escape '\\')
            """)
    List<String> findActiveFilenamesByUserIdAndPathAndFilenamePrefix(@Param("userId") Long userId,
                                                                     @Param("path") String path,
                                                                     @Param("filename") String filename,
                                                                     @Param("filenamePrefix") String filenamePrefix);

    @Query("""
            select f from StoredFile f
            where f.userId = :userId and f.path = :path and f.filename = :filename and f.deletedAt is null
            """)
    Optional<StoredFile> findByUserIdAndPathAndFilename(@Param("userId") Long userId,
                                                        @Param("path") String path,
                                                        @Param("filename") String filename);

    @Query("""
            select f from StoredFile f
            where f.userId = :userId and f.path = :path and f.deletedAt is null
            order by f.directory desc, f.createdAt desc
            """)
    Page<StoredFile> findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(@Param("userId") Long userId,
                                                                          @Param("path") String path,
                                                                          Pageable pageable);

    @Query(value = """
            select distinct f.path
            from portal_file f
            where f.user_id = :userId
              and f.is_directory = true
              and f.deleted_at is null
              and f.path in (:paths)
            """, nativeQuery = true)
    List<String> findDirectoryPathsWithChildDirectories(@Param("userId") Long userId,
                                                        @Param("paths") Collection<String> paths);

    @Query("""
            select f from StoredFile f
            where f.userId = :userId
              and f.deletedAt is null
              and (:name is null or :name = '' or lower(f.filename) like lower(concat('%', :name, '%')))
              and (
                    :category is null
                    or :category = ''
                    or f.searchCategory = :category
                    or (
                        f.searchCategory is null
                        and (
                            (
                                :category = 'image'
                                and (
                                    lower(coalesce(f.contentType, '')) like 'image/%'
                                    or lower(f.filename) like '%.png'
                                    or lower(f.filename) like '%.jpg'
                                    or lower(f.filename) like '%.jpeg'
                                    or lower(f.filename) like '%.gif'
                                    or lower(f.filename) like '%.webp'
                                    or lower(f.filename) like '%.bmp'
                                    or lower(f.filename) like '%.svg'
                                    or lower(f.filename) like '%.heic'
                                    or lower(f.filename) like '%.heif'
                                    or lower(f.filename) like '%.avif'
                                )
                            )
                            or (
                                :category = 'video'
                                and (
                                    lower(coalesce(f.contentType, '')) like 'video/%'
                                    or lower(f.filename) like '%.mp4'
                                    or lower(f.filename) like '%.mov'
                                    or lower(f.filename) like '%.m4v'
                                    or lower(f.filename) like '%.mkv'
                                    or lower(f.filename) like '%.avi'
                                    or lower(f.filename) like '%.webm'
                                )
                            )
                            or (
                                :category = 'audio'
                                and (
                                    lower(coalesce(f.contentType, '')) like 'audio/%'
                                    or lower(f.filename) like '%.mp3'
                                    or lower(f.filename) like '%.wav'
                                    or lower(f.filename) like '%.flac'
                                    or lower(f.filename) like '%.aac'
                                    or lower(f.filename) like '%.m4a'
                                    or lower(f.filename) like '%.ogg'
                                )
                            )
                            or (
                                :category = 'document'
                                and (
                                    lower(coalesce(f.contentType, '')) in (
                                        'application/pdf',
                                        'application/msword',
                                        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                                        'application/vnd.ms-excel',
                                        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                                        'application/vnd.ms-powerpoint',
                                        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
                                        'text/plain',
                                        'text/markdown'
                                    )
                                    or lower(f.filename) like '%.pdf'
                                    or lower(f.filename) like '%.doc'
                                    or lower(f.filename) like '%.docx'
                                    or lower(f.filename) like '%.xls'
                                    or lower(f.filename) like '%.xlsx'
                                    or lower(f.filename) like '%.ppt'
                                    or lower(f.filename) like '%.pptx'
                                    or lower(f.filename) like '%.txt'
                                    or lower(f.filename) like '%.md'
                                )
                            )
                        )
                    )
              )
              and (:directory is null or f.directory = :directory)
              and (:sizeGte is null or f.size >= :sizeGte)
              and (:sizeLte is null or f.size <= :sizeLte)
              and (:createdGte is null or f.createdAt >= :createdGte)
              and (:createdLte is null or f.createdAt <= :createdLte)
              and (:updatedGte is null or coalesce(f.updatedAt, f.createdAt) >= :updatedGte)
              and (:updatedLte is null or coalesce(f.updatedAt, f.createdAt) <= :updatedLte)
            order by f.directory desc, coalesce(f.updatedAt, f.createdAt) desc, f.createdAt desc
            """)
    Page<StoredFile> searchUserFiles(@Param("userId") Long userId,
                                     @Param("name") String name,
                                     @Param("category") String category,
                                     @Param("directory") Boolean directory,
                                     @Param("sizeGte") Long sizeGte,
                                     @Param("sizeLte") Long sizeLte,
                                     @Param("createdGte") LocalDateTime createdGte,
                                     @Param("createdLte") LocalDateTime createdLte,
                                     @Param("updatedGte") LocalDateTime updatedGte,
                                     @Param("updatedLte") LocalDateTime updatedLte,
                                     Pageable pageable);

    Optional<StoredFile> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    Optional<StoredFile> findByIdAndDeletedAtIsNull(Long id);

    List<StoredFile> findByIdInAndDeletedAtIsNull(Set<Long> ids);

    @Query("""
            select f from StoredFile f
            where f.userId = :userId and f.deletedAt is null and (f.path = :path or f.path like concat(:path, '/%'))
            order by f.createdAt asc
            """)
    List<StoredFile> findByUserIdAndPathEqualsOrDescendant(@Param("userId") Long userId,
                                                           @Param("path") String path);

    @Query("""
            select coalesce(sum(f.size), 0)
            from StoredFile f
            where f.userId = :userId and f.directory = false and f.deletedAt is null
            """)
    long sumFileSizeByUserId(@Param("userId") Long userId);

    @Query("""
            select f.userId as userId, coalesce(sum(f.size), 0) as usedStorageBytes
            from StoredFile f
            where f.userId in :userIds and f.directory = false and f.deletedAt is null
            group by f.userId
            """)
    List<UserStorageUsageProjection> sumFileSizeByUserIds(@Param("userIds") Collection<Long> userIds);

    @Query("""
            select coalesce(sum(f.size), 0)
            from StoredFile f
            where f.directory = false
            """)
    long sumAllFileSize();

    List<StoredFile> findTop12ByUserIdAndDirectoryFalseAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    List<StoredFile> findTop20ByUserIdAndFavoriteTrueAndDeletedAtIsNullOrderByUpdatedAtDesc(Long userId);

    @Query("""
            select f from StoredFile f
            where f.userId = :userId and f.deletedAt is not null and f.recycleRoot = true
            order by f.deletedAt desc
            """)
    Page<StoredFile> findRecycleBinRootsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select f from StoredFile f
            where f.recycleGroupId = :groupId
            order by length(coalesce(f.recycleOriginalPath, f.path)) asc, f.directory desc, f.createdAt asc
            """)
    List<StoredFile> findByRecycleGroupId(@Param("groupId") String groupId);

    @Query("""
            select f from StoredFile f
            where f.deletedAt is not null and f.deletedAt < :cutoff
            order by f.deletedAt asc
            """)
    List<StoredFile> findByDeletedAtBefore(@Param("cutoff") java.time.LocalDateTime cutoff);

    @Query("""
            select count(f)
            from StoredFile f
            where f.blobId = :blobId
            """)
    long countByBlobId(@Param("blobId") Long blobId);

    @Query("""
            select count(f)
            from StoredFile f
            where f.favorite = true and f.deletedAt is null
            """)
    long countFavoriteFilesAsAdmin();

    @Query("""
            select f from StoredFile f
            where f.id = :id
            """)
    Optional<StoredFile> findDetailedById(@Param("id") Long id);

    List<StoredFile> findAllByDirectoryFalseAndBlobIdIsNull();

    List<StoredFile> findAllByDirectoryFalseAndBlobIdIsNotNullAndPrimaryEntityIdIsNull();
}
