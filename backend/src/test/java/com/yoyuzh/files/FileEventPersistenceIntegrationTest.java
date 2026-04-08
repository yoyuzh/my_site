package com.yoyuzh.files;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.admin.AdminMetricsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:file_events_service_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-file-events-service"
        }
)
class FileEventPersistenceIntegrationTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private FileEventRepository fileEventRepository;

    @MockBean
    private StoredFileRepository storedFileRepository;

    @MockBean
    private FileBlobRepository fileBlobRepository;

    @MockBean
    private FileEntityRepository fileEntityRepository;

    @MockBean
    private StoredFileEntityRepository storedFileEntityRepository;

    @MockBean
    private FileContentStorage fileContentStorage;

    @MockBean
    private FileShareLinkRepository fileShareLinkRepository;

    @MockBean
    private AdminMetricsService adminMetricsService;

    @MockBean
    private StoragePolicyService storagePolicyService;

    @BeforeEach
    void setUp() {
        fileEventRepository.deleteAll();
    }

    @Test
    void shouldPersistRenameEventWhenFileChanges() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setCreatedAt(LocalDateTime.now());

        StoredFile file = new StoredFile();
        file.setId(10L);
        file.setUser(user);
        file.setFilename("notes.txt");
        file.setPath("/docs");
        file.setContentType("text/plain");
        file.setSize(5L);
        file.setDirectory(false);
        file.setCreatedAt(LocalDateTime.now());

        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(file));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "paper.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataResponse response = fileService.rename(user, 10L, "paper.txt");

        assertThat(response.filename()).isEqualTo("paper.txt");
        assertThat(fileEventRepository.count()).isEqualTo(1L);

        FileEvent event = fileEventRepository.findAll().get(0);
        assertThat(event.getEventType()).isEqualTo(FileEventType.RENAMED);
        assertThat(event.getFileId()).isEqualTo(10L);
        assertThat(event.getFromPath()).isEqualTo("/docs/notes.txt");
        assertThat(event.getToPath()).isEqualTo("/docs/paper.txt");
        assertThat(event.getPayloadJson()).contains("\"action\":\"RENAMED\"");
        assertThat(event.getPayloadJson()).contains("\"filename\":\"paper.txt\"");
    }
}
