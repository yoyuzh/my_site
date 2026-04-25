package com.yoyuzh.files.workspace.internal.infra;

import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StoredFileRepositoryIntegrationTest {

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Test
    void shouldReturnDirectoryLogicalPathsThatContainChildDirectories() {
        storedFileRepository.saveAndFlush(directory(7L, "/", "文档"));
        storedFileRepository.saveAndFlush(directory(7L, "/", "图片"));
        storedFileRepository.saveAndFlush(directory(7L, "/文档", "子目录验证"));

        List<String> result = storedFileRepository.findDirectoryPathsWithChildDirectories(
                7L,
                List.of("/文档", "/图片")
        );

        assertThat(result).containsExactly("/文档");
    }

    private StoredFile directory(Long userId, String path, String filename) {
        StoredFile storedFile = new StoredFile();
        storedFile.setUserId(userId);
        storedFile.setPath(path);
        storedFile.setFilename(filename);
        storedFile.setContentType("directory");
        storedFile.setSize(0L);
        storedFile.setDirectory(true);
        return storedFile;
    }
}
