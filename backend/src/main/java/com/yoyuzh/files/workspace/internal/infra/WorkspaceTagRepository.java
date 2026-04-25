package com.yoyuzh.files.workspace.internal.infra;

import com.yoyuzh.files.workspace.internal.domain.WorkspaceTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceTagRepository extends JpaRepository<WorkspaceTag, Long> {

    List<WorkspaceTag> findByUserIdOrderByNameAsc(Long userId);

    Optional<WorkspaceTag> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    @Query("""
            select t from WorkspaceTag t
            join WorkspaceFileTag ft on ft.tagId = t.id
            where ft.userId = :userId and ft.fileId = :fileId
            order by lower(t.name) asc, t.id asc
            """)
    List<WorkspaceTag> findAssignedTags(@Param("userId") Long userId, @Param("fileId") Long fileId);
}
