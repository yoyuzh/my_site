package com.yoyuzh.files.events;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FileEventRepository extends JpaRepository<FileEvent, Long> {
}
