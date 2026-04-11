package com.yoyuzh.files.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileEntityRepository extends JpaRepository<StoredFileEntity, Long> {

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
}
