package com.yoyuzh.files.sharing.internal.infra;

import com.yoyuzh.files.sharing.internal.domain.FileShareLink;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.Optional;

public interface FileShareLinkRepository extends JpaRepository<FileShareLink, Long> {

    @EntityGraph(attributePaths = {"owner", "file", "file.user", "file.blob"})
    Optional<FileShareLink> findByToken(String token);

    @EntityGraph(attributePaths = {"owner", "file", "file.user", "file.blob"})
    Page<FileShareLink> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "file", "file.user", "file.blob"})
    Optional<FileShareLink> findByIdAndOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"owner", "file", "file.user", "file.primaryEntity", "file.blob"})
    @Query("""
            select share from FileShareLink share
            join share.owner owner
            join share.file file
            where (:userQuery is null or :userQuery = ''
                or lower(owner.username) like lower(concat('%', :userQuery, '%'))
                or lower(owner.email) like lower(concat('%', :userQuery, '%')))
              and (:fileName is null or :fileName = ''
                or lower(file.filename) like lower(concat('%', :fileName, '%')))
              and (:token is null or :token = ''
                or lower(share.token) like lower(concat('%', :token, '%')))
              and (:passwordProtected is null
                or (:passwordProtected = true and share.passwordHash is not null and share.passwordHash <> '')
                or (:passwordProtected = false and (share.passwordHash is null or share.passwordHash = '')))
              and (:expired is null
                or (:expired = true and share.expiresAt is not null and share.expiresAt < :now)
                or (:expired = false and (share.expiresAt is null or share.expiresAt >= :now)))
            """)
    Page<FileShareLink> searchAdminShares(@Param("userQuery") String userQuery,
                                          @Param("fileName") String fileName,
                                          @Param("token") String token,
                                          @Param("passwordProtected") Boolean passwordProtected,
                                          @Param("expired") Boolean expired,
                                          @Param("now") LocalDateTime now,
                                          Pageable pageable);
}
