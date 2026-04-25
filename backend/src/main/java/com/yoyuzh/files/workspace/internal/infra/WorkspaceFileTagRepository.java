package com.yoyuzh.files.workspace.internal.infra;

import com.yoyuzh.files.workspace.internal.domain.WorkspaceFileTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceFileTagRepository extends JpaRepository<WorkspaceFileTag, Long> {

    boolean existsByUserIdAndFileIdAndTagId(Long userId, Long fileId, Long tagId);

    void deleteByUserIdAndFileIdAndTagId(Long userId, Long fileId, Long tagId);

    void deleteAllByTagId(Long tagId);
}
