package com.yoyuzh.files.search.internal.infra;

import com.yoyuzh.files.search.internal.domain.FileEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileEventRepository extends JpaRepository<FileEvent, Long> {
}
