package com.yoyuzh.files.workspace.internal.infra;

import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StoredFileRepositoryIntegrationTest {

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    void shouldFilterImageCategoryByContentType() {
        StoredFile imageFile = storedFileRepository.saveAndFlush(file(7L, "/photos", "cover.png", "image/png"));
        storedFileRepository.saveAndFlush(file(7L, "/docs", "notes.txt", "text/plain"));

        assertThat(imageFile.getSearchCategory()).isEqualTo("image");

        Page<StoredFile> result = storedFileRepository.searchUserFiles(
                7L,
                null,
                "image",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(StoredFile::getFilename).containsExactly("cover.png");
    }

    @Test
    void shouldFilterAudioCategoryByFilenameExtensionWhenContentTypeMissing() {
        StoredFile audioFile = storedFileRepository.saveAndFlush(file(7L, "/music", "theme.flac", null));
        storedFileRepository.saveAndFlush(file(7L, "/music", "poster.png", null));

        assertThat(audioFile.getSearchCategory()).isEqualTo("audio");

        Page<StoredFile> result = storedFileRepository.searchUserFiles(
                7L,
                null,
                "audio",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(StoredFile::getFilename).containsExactly("theme.flac");
    }

    @Test
    void shouldFilterDocumentCategoryByFilenameExtensionWhenContentTypeMissing() {
        StoredFile documentFile = storedFileRepository.saveAndFlush(file(7L, "/docs", "report.docx", null));
        storedFileRepository.saveAndFlush(file(7L, "/docs", "clip.mp4", null));

        assertThat(documentFile.getSearchCategory()).isEqualTo("document");

        Page<StoredFile> result = storedFileRepository.searchUserFiles(
                7L,
                null,
                "document",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(StoredFile::getFilename).containsExactly("report.docx");
    }

    @Test
    void shouldRefreshSearchCategoryWhenFilenameChanges() {
        StoredFile storedFile = storedFileRepository.saveAndFlush(file(7L, "/docs", "draft.tmp", "application/octet-stream"));

        assertThat(storedFile.getSearchCategory()).isNull();

        storedFile.renameTo("draft.mp4");
        StoredFile updated = storedFileRepository.saveAndFlush(storedFile);

        assertThat(updated.getSearchCategory()).isEqualTo("video");
    }

    @Test
    void shouldFilterLegacyFilesWhenSearchCategoryIsMissing() {
        StoredFile imageFile = storedFileRepository.saveAndFlush(file(7L, "/photos", "cover.png", "image/png"));
        StoredFile videoFile = storedFileRepository.saveAndFlush(file(7L, "/videos", "clip.mp4", "video/mp4"));
        StoredFile audioFile = storedFileRepository.saveAndFlush(file(7L, "/music", "theme.mp3", "audio/mpeg"));
        StoredFile documentFile = storedFileRepository.saveAndFlush(file(7L, "/docs", "report.pdf", "application/pdf"));

        clearSearchCategory(imageFile.getId());
        clearSearchCategory(videoFile.getId());
        clearSearchCategory(audioFile.getId());
        clearSearchCategory(documentFile.getId());

        assertThat(searchByCategory("image")).extracting(StoredFile::getFilename).containsExactly("cover.png");
        assertThat(searchByCategory("video")).extracting(StoredFile::getFilename).containsExactly("clip.mp4");
        assertThat(searchByCategory("audio")).extracting(StoredFile::getFilename).containsExactly("theme.mp3");
        assertThat(searchByCategory("document")).extracting(StoredFile::getFilename).containsExactly("report.pdf");
    }

    private List<StoredFile> searchByCategory(String category) {
        Page<StoredFile> result = storedFileRepository.searchUserFiles(
                7L,
                null,
                category,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        );
        return result.getContent();
    }

    private void clearSearchCategory(Long fileId) {
        entityManager.createNativeQuery("update portal_file set search_category = null where id = :id")
                .setParameter("id", fileId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
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

    private StoredFile file(Long userId, String path, String filename, String contentType) {
        StoredFile storedFile = new StoredFile();
        storedFile.setUserId(userId);
        storedFile.setPath(path);
        storedFile.setFilename(filename);
        storedFile.setContentType(contentType);
        storedFile.setSize(128L);
        storedFile.setDirectory(false);
        return storedFile;
    }
}
