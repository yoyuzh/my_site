package com.yoyuzh.files.content.internal.infra;

import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileEntityRepository extends JpaRepository<FileEntity, Long> {

    Optional<FileEntity> findByObjectKeyAndEntityType(String objectKey, FileEntityType entityType);

    long countByStoragePolicyIdAndEntityType(Long storagePolicyId, FileEntityType entityType);

    List<FileEntity> findByStoragePolicyIdAndEntityTypeOrderByIdAsc(Long storagePolicyId, FileEntityType entityType);

    @Query("""
            select entity from FileEntity entity
            where (:storagePolicyId is null or entity.storagePolicyId = :storagePolicyId)
              and (:entityType is null or entity.entityType = :entityType)
              and (:objectKey is null or :objectKey = ''
                  or lower(entity.objectKey) like lower(concat('%', :objectKey, '%')))
              and (:userQuery is null or :userQuery = '' or exists (
                    select 1 from StoredFileEntity relation, StoredFile storedFile, User owner
                    where relation.fileEntity.id = entity.id
                      and storedFile.id = relation.storedFileId
                      and owner.id = storedFile.userId
                      and (
                          lower(owner.username) like lower(concat('%', :userQuery, '%'))
                          or lower(owner.email) like lower(concat('%', :userQuery, '%'))
                      )
              ))
            """)
    Page<FileEntity> searchAdminEntities(@Param("userQuery") String userQuery,
                                         @Param("storagePolicyId") Long storagePolicyId,
                                         @Param("objectKey") String objectKey,
                                         @Param("entityType") FileEntityType entityType,
                                         Pageable pageable);
}
