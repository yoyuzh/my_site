package com.yoyuzh.ops.admin.internal.infra;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, Long> {

    @Query("""
            select l from AdminAuditLogEntity l
            where (:actorQuery = '' or lower(l.actorUsername) like lower(concat('%', :actorQuery, '%')))
              and (:actionType = '' or l.actionType = :actionType)
              and (:targetType = '' or l.targetType = :targetType)
              and (:targetId is null or l.targetId = :targetId)
            """)
    Page<AdminAuditLogEntity> search(
            @Param("actorQuery") String actorQuery,
            @Param("actionType") String actionType,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            Pageable pageable
    );
}
