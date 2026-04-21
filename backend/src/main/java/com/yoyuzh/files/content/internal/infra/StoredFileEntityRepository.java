package com.yoyuzh.files.content.internal.infra;

import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.domain.StoredFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoredFileEntityRepository extends JpaRepository<StoredFileEntity, Long> {

    interface FileEntityLinkStatsProjection {
        Long getFileEntityId();

        Long getLinkedStoredFileCount();

        Long getLinkedOwnerCount();

        String getSampleOwnerUsername();

        String getSampleOwnerEmail();
    }

    @Query("""
            select count(distinct relation.storedFile.id)
            from StoredFileEntity relation
            where relation.fileEntity.storagePolicyId = :storagePolicyId
              and relation.fileEntity.entityType = :entityType
            """)
    long countDistinctStoredFilesByStoragePolicyIdAndEntityType(@Param("storagePolicyId") Long storagePolicyId,
                                                                @Param("entityType") FileEntityType entityType);

    long countByFileEntityId(Long fileEntityId);

    @Query("""
            select count(distinct relation.storedFile.user.id)
            from StoredFileEntity relation
            where relation.fileEntity.id = :fileEntityId
            """)
    long countDistinctOwnersByFileEntityId(@Param("fileEntityId") Long fileEntityId);

    @Query("""
            select min(owner.username)
            from StoredFileEntity relation
            join relation.storedFile storedFile
            join storedFile.user owner
            where relation.fileEntity.id = :fileEntityId
            """)
    String findSampleOwnerUsernameByFileEntityId(@Param("fileEntityId") Long fileEntityId);

    @Query("""
            select min(owner.email)
            from StoredFileEntity relation
            join relation.storedFile storedFile
            join storedFile.user owner
            where relation.fileEntity.id = :fileEntityId
            """)
    String findSampleOwnerEmailByFileEntityId(@Param("fileEntityId") Long fileEntityId);

    @Query("""
            select relation.fileEntity.id as fileEntityId,
                   count(distinct relation.storedFile.id) as linkedStoredFileCount,
                   count(distinct owner.id) as linkedOwnerCount,
                   min(owner.username) as sampleOwnerUsername,
                   min(owner.email) as sampleOwnerEmail
            from StoredFileEntity relation
            join relation.storedFile storedFile
            join storedFile.user owner
            where relation.fileEntity.id in :fileEntityIds
            group by relation.fileEntity.id
            """)
    List<FileEntityLinkStatsProjection> findAdminLinkStatsByFileEntityIds(@Param("fileEntityIds") Collection<Long> fileEntityIds);
}
