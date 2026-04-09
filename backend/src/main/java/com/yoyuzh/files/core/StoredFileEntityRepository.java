package com.yoyuzh.files.core;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileEntityRepository extends JpaRepository<StoredFileEntity, Long> {
}
