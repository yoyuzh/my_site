package com.yoyuzh.files.core;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:recycle_bin_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-recycle-bin"
        }
)
@AutoConfigureMockMvc
class RecycleBinControllerIntegrationTest {

    private static final Path STORAGE_ROOT = Path.of("./target/test-storage-recycle-bin").toAbsolutePath().normalize();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private FileBlobRepository fileBlobRepository;

    private Long deletedFileId;

    @BeforeEach
    void setUp() throws Exception {
        storedFileRepository.deleteAll();
        fileBlobRepository.deleteAll();
        userRepository.deleteAll();

        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
        }
        Files.createDirectories(STORAGE_ROOT);

        User owner = new User();
        owner.setUsername("alice");
        owner.setEmail("alice@example.com");
        owner.setPhoneNumber("13800138000");
        owner.setPasswordHash("encoded-password");
        owner.setCreatedAt(LocalDateTime.now());
        owner = userRepository.save(owner);

        StoredFile docsDirectory = new StoredFile();
        docsDirectory.setUser(owner);
        docsDirectory.setFilename("docs");
        docsDirectory.setPath("/");
        docsDirectory.setContentType("directory");
        docsDirectory.setSize(0L);
        docsDirectory.setDirectory(true);
        storedFileRepository.save(docsDirectory);

        FileBlob blob = new FileBlob();
        blob.setObjectKey("blobs/recycle-notes");
        blob.setContentType("text/plain");
        blob.setSize(5L);
        blob.setCreatedAt(LocalDateTime.now());
        blob = fileBlobRepository.save(blob);

        StoredFile file = new StoredFile();
        file.setUser(owner);
        file.setFilename("notes.txt");
        file.setPath("/docs");
        file.setContentType("text/plain");
        file.setSize(5L);
        file.setDirectory(false);
        file.setBlob(blob);
        deletedFileId = storedFileRepository.save(file).getId();

        Path blobPath = STORAGE_ROOT.resolve("blobs").resolve("recycle-notes");
        Files.createDirectories(blobPath.getParent());
        Files.writeString(blobPath, "hello", StandardCharsets.UTF_8);
    }

    @Test
    void shouldDeleteListAndRestoreFileThroughRecycleBinApi() throws Exception {
        mockMvc.perform(delete("/api/files/{fileId}", deletedFileId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/files/list")
                        .with(user("alice"))
                        .param("path", "/docs")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        String recycleResponse = mockMvc.perform(get("/api/files/recycle-bin")
                        .with(user("alice"))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].filename").value("notes.txt"))
                .andExpect(jsonPath("$.data.items[0].path").value("/docs"))
                .andExpect(jsonPath("$.data.items[0].deletedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number recycleRootId = JsonPath.read(recycleResponse, "$.data.items[0].id");

        mockMvc.perform(post("/api/files/recycle-bin/{fileId}/restore", recycleRootId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("notes.txt"))
                .andExpect(jsonPath("$.data.path").value("/docs"));

        mockMvc.perform(get("/api/files/recycle-bin")
                        .with(user("alice"))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(get("/api/files/list")
                        .with(user("alice"))
                        .param("path", "/docs")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].filename").value("notes.txt"));

        StoredFile restoredFile = storedFileRepository.findById(deletedFileId).orElseThrow();
        assertThat(restoredFile.getDeletedAt()).isNull();
        assertThat(restoredFile.getRecycleGroupId()).isNull();
        assertThat(restoredFile.getRecycleOriginalPath()).isNull();
    }
}
