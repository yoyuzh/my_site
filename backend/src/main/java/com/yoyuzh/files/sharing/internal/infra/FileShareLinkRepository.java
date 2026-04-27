package com.yoyuzh.files.sharing.internal.infra;

import com.yoyuzh.files.sharing.internal.domain.FileShareLink;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.Optional;

public interface FileShareLinkRepository extends JpaRepository<FileShareLink, Long> {

    Optional<FileShareLink> findByToken(String token);

    Optional<FileShareLink> findByTokenAndCancelledAtIsNull(String token);

    Page<FileShareLink> findByOwnerIdAndCancelledAtIsNullOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    Optional<FileShareLink> findByIdAndOwnerIdAndCancelledAtIsNull(Long id, Long ownerId);

    @Query("""
            select share from FileShareLink share
            join StoredFile file on file.id = share.fileId
            where share.cancelledAt is null
              and (:userQuery is null or :userQuery = ''
                or exists (
                    select 1 from User owner
                    where owner.id = share.ownerId
                      and (
                          lower(owner.username) like lower(concat('%', :userQuery, '%'))
                          or lower(owner.email) like lower(concat('%', :userQuery, '%'))
                      )
                ))
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

    @Query("""
            select coalesce(sum(share.downloadCount), 0)
            from FileShareLink share
            """)
    long sumDownloadCountAsAdmin();
}
