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
}
